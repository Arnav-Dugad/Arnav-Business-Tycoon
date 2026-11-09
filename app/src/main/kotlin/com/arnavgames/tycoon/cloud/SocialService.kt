package com.arnavgames.tycoon.cloud

import android.content.Context
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.game.GameSession
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

data class BoardEntry(val uid: String, val name: String, val lnw: Double, val fairLnw: Double, val boosted: Boolean, val generation: Int, val isMe: Boolean)
data class FriendRequest(val id: String, val from: String, val to: String, val fromName: String)
data class Friend(
    val uid: String, val name: String, val nwLog10: Double, val boosted: Boolean, val giftSentToday: Boolean,
    val avatarId: String? = null, val frame: String? = null, val title: String? = null, val generation: Int = 1, val showcase: List<String> = emptyList(),
)
data class Gift(val id: String, val from: String, val fromName: String, val amount: Long)
data class SealedLot(val weekId: String, val itemId: String, val myBid: Money?, val closesInMs: Long)
data class LotResult(val weekId: String, val itemId: String, val winnerName: String?, val winningBid: Money?, val iWon: Boolean, val bids: Int)

/**
 * Social features on Firestore (docs/GDD.md §14, docs/ARCHITECTURE.md §7.2): friend codes and requests, public
 * profiles, sharded leaderboards (global, friends, fair play, weekly league), daily gifts and weekly Sealed Lots.
 * Every write is checked by the security rules; the client only adds convenience on top.
 */
class SocialService(private val cloud: CloudService, private val session: GameSession, app: Context) {
    private val prefs = app.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val db get() = cloud.firestore
    private fun me() = cloud.user.value?.uid ?: error("Sign in first")

    fun canUse(): Boolean = cloud.user.value?.let { !it.guest && (it.verified || it.google) } ?: false

    var displayName: String
        get() = prefs.getString("name", null) ?: "Founder-${(cloud.user.value?.uid ?: "0000").takeLast(4).uppercase()}"
        set(v) { prefs.edit().putString("name", v.trim().take(24)).apply() }

    /** An 8-character code derived from the account id (A–Z, 0–9). */
    fun friendCode(uid: String = me()): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val h = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        return (0 until 8).map { alphabet[(h[it].toInt() and 0xFF) % alphabet.length] }.joinToString("")
    }

    private fun nwLog10(): Double = session.state.value?.let { s ->
        val d = session.engine.economy.netWorth(s).toDollarsDouble(); if (d > 1) kotlin.math.log10(d) else 0.0
    } ?: 0.0

    private fun shard(uid: String) = (uid.hashCode() and 0x7FFFFFFF) % 4

    /** Publishes the profile, the friend code and leaderboard entries (at most once every 10 minutes). */
    suspend fun publish(force: Boolean = false) = cloud.cloud {
        val uid = me()
        val s = session.state.value ?: error("No game loaded")
        // Only the main game speaks for you online; a sandbox (a save that used cheats) never reaches the boards.
        if (session.slot.value != 0) return@cloud Unit
        val sandbox = com.arnavgames.tycoon.core.engine.Cheats.isSandbox(s)
        val boosted = s.store.boosted
        val lnw = nwLog10()
        val code = friendCode(uid)
        runCatching { db.document("friendCodes/$code").set(mapOf("uid" to uid)).await() } // create-only; fine if it exists
        db.document("profiles/$uid").set(mapOf(
            "name" to displayName, "friendCode" to code, "avatarId" to (s.store.avatar ?: "crown"), "frame" to (s.store.frame ?: ""), "title" to (s.store.title ?: ""),
            "gen" to s.dynasty.generation.toLong(), "nwLog10" to lnw, "fairNwLog10" to if (boosted) 0.0 else lnw, "boosted" to boosted,
            "showcase" to s.luxury.owned.keys.take(6).toList(), "updatedAt" to FieldValue.serverTimestamp(),
        )).await()
        val last = prefs.getLong("boardAt", 0)
        if (!sandbox && (force || System.currentTimeMillis() - last > 10 * 60_000)) {
            val entry = mapOf("n" to displayName, "lnw" to lnw, "flnw" to if (boosted) 0.0 else lnw, "b" to boosted, "g" to s.dynasty.generation.toLong(), "t" to FieldValue.serverTimestamp())
            db.document("boards/global/shards/${shard(uid)}").set(mapOf("entries" to mapOf(uid to entry)), cloud.merge()).await()
            // Weekly league: growth since this week's first publish (new and veteran players compete fairly).
            val week = leagueId()
            val base = prefs.getFloat("league:$week", Float.NaN).let { if (it.isNaN()) { prefs.edit().putFloat("league:$week", lnw.toFloat()).apply(); lnw } else it.toDouble() }
            val growth = (lnw - base).coerceAtLeast(0.0)
            db.document("boards/$week/shards/${shard(uid)}").set(mapOf("entries" to mapOf(uid to entry + mapOf("lnw" to growth, "flnw" to if (boosted) 0.0 else growth))), cloud.merge()).await()
            prefs.edit().putLong("boardAt", System.currentTimeMillis()).apply()
            cloud.countWrites(4)
        } else cloud.countWrites(2)
        Unit
    }

    fun leagueId(): String {
        val d = LocalDate.now(ZoneOffset.UTC)
        return "w${d.get(IsoFields.WEEK_BASED_YEAR)}-${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString().padStart(2, '0')}"
    }

    /** All entries of a board (4 reads). */
    suspend fun board(boardId: String = "global") = cloud.cloud {
        val uid = me()
        val out = mutableListOf<BoardEntry>()
        for (i in 0..3) {
            val snap = db.document("boards/$boardId/shards/$i").get().await()
            @Suppress("UNCHECKED_CAST") val entries = snap.get("entries") as? Map<String, Map<String, Any?>> ?: emptyMap()
            entries.forEach { (id, e) ->
                out += BoardEntry(id, e["n"] as? String ?: "?", (e["lnw"] as? Number)?.toDouble() ?: 0.0, (e["flnw"] as? Number)?.toDouble() ?: 0.0,
                    e["b"] as? Boolean ?: false, (e["g"] as? Number)?.toInt() ?: 1, id == uid)
            }
        }
        cloud.countReads(4)
        out.sortedByDescending { it.lnw }
    }

    // ---------------------------------------------------------------- friends

    suspend fun sendRequest(code: String) = cloud.cloud {
        val uid = me()
        val target = db.document("friendCodes/${code.trim().uppercase()}").get().await().getString("uid") ?: error("No player has that code")
        if (target == uid) error("That's your own code")
        db.document("friendRequests/${target}_$uid").set(mapOf("from" to uid, "to" to target, "createdAt" to FieldValue.serverTimestamp())).await()
        prefs.edit().putStringSet("sent", (prefs.getStringSet("sent", emptySet()) ?: emptySet()) + target).apply()
        cloud.countReads(1); cloud.countWrites(1)
        Unit
    }

    suspend fun incoming() = cloud.cloud {
        val uid = me()
        val snaps = db.collection("friendRequests").whereEqualTo("to", uid).get().await()
        cloud.countReads(snaps.size().toLong().coerceAtLeast(1))
        val sent = prefs.getStringSet("sent", emptySet()) ?: emptySet()
        val out = mutableListOf<FriendRequest>()
        for (d in snaps.documents) {
            val from = d.getString("from") ?: continue
            // If I had asked them too, this is their acceptance: add them straight away.
            if (from in sent) { addFriend(from); d.reference.delete().await(); continue }
            val name = db.document("profiles/$from").get().await().getString("name") ?: "A founder"
            out += FriendRequest(d.id, from, uid, name)
        }
        out
    }

    private suspend fun addFriend(uid: String) {
        db.document("social/${me()}").set(mapOf("friends" to FieldValue.arrayUnion(uid)), cloud.merge()).await()
        cloud.countWrites(1)
    }

    suspend fun accept(r: FriendRequest) = cloud.cloud {
        addFriend(r.from)
        db.document("friendRequests/${r.id}").delete().await()
        // Tell them (a request back, which their client accepts automatically).
        db.document("friendRequests/${r.from}_${me()}").set(mapOf("from" to me(), "to" to r.from, "createdAt" to FieldValue.serverTimestamp())).await()
        cloud.countWrites(2)
        Unit
    }

    suspend fun decline(r: FriendRequest) = cloud.cloud { db.document("friendRequests/${r.id}").delete().await(); cloud.countWrites(1); Unit }

    suspend fun friends() = cloud.cloud {
        val uid = me()
        @Suppress("UNCHECKED_CAST") val ids = db.document("social/$uid").get().await().get("friends") as? List<String> ?: emptyList()
        val today = dayKey()
        val sentToday = prefs.getStringSet("gifts:$today", emptySet()) ?: emptySet()
        val out = ids.take(30).mapNotNull { id ->
            val p = db.document("profiles/$id").get().await()
            @Suppress("UNCHECKED_CAST")
            if (!p.exists()) null else Friend(
                id, p.getString("name") ?: "?", p.getDouble("nwLog10") ?: 0.0, p.getBoolean("boosted") ?: false, id in sentToday,
                avatarId = p.getString("avatarId"), frame = p.getString("frame")?.takeIf { it.isNotEmpty() }, title = p.getString("title")?.takeIf { it.isNotEmpty() },
                generation = (p.getLong("gen") ?: 1L).toInt(), showcase = (p.get("showcase") as? List<String>).orEmpty(),
            )
        }
        cloud.countReads(1L + out.size)
        out
    }

    // ---------------------------------------------------------------- gifts

    private fun dayKey() = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")

    suspend fun sendGift(friendUid: String) = cloud.cloud {
        val day = dayKey()
        db.document("gifts/$friendUid/inbox/${me()}_$day").set(mapOf("from" to me(), "kind" to "favor", "amount" to 10L, "createdAt" to FieldValue.serverTimestamp())).await()
        prefs.edit().putStringSet("gifts:$day", (prefs.getStringSet("gifts:$day", emptySet()) ?: emptySet()) + friendUid).apply()
        cloud.countWrites(1)
        Unit
    }

    suspend fun inbox() = cloud.cloud {
        val snaps = db.collection("gifts/${me()}/inbox").get().await()
        cloud.countReads(snaps.size().toLong().coerceAtLeast(1))
        snaps.documents.map { d -> Gift(d.id, d.getString("from") ?: "", d.getString("from")?.takeLast(4) ?: "", d.getLong("amount") ?: 0) }
    }

    suspend fun claim(g: Gift) = cloud.cloud {
        session.dispatch(Command.ReceiveGift(g.amount.coerceIn(1, 50)))
        db.document("gifts/${me()}/inbox/${g.id}").delete().await()
        cloud.countWrites(1)
        Unit
    }

    // ---------------------------------------------------------------- Sealed Lots

    fun weekId(offset: Long = 0): String = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset).toString()

    /** This week's piece: chosen the same way on every device from the week id. */
    fun lotItem(weekId: String): String {
        val pool = session.engine.content.luxury.items.filter { it.rarity != "common" }.sortedBy { it.id }
        return pool[(weekId.hashCode() and 0x7FFFFFFF) % pool.size].id
    }

    suspend fun currentLot() = cloud.cloud {
        val week = weekId()
        val mine = db.document("lots/$week/bids/${me()}").get().await()
        cloud.countReads(1)
        val close = LocalDate.parse(week).plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        SealedLot(week, lotItem(week), mine.getString("amount")?.let { Money.ofDollars(java.math.BigDecimal(it)) }, close - System.currentTimeMillis())
    }

    suspend fun bid(amount: Money) = cloud.cloud {
        val week = weekId()
        val d = amount.toDollarsDouble()
        db.document("lots/$week/bids/${me()}").set(mapOf(
            "lotId" to lotItem(week), "amountLog10" to if (d > 1) kotlin.math.log10(d) else 0.0,
            "amount" to java.math.BigDecimal(amount.cents).movePointLeft(2).toPlainString(), "createdAt" to FieldValue.serverTimestamp(),
        )).await()
        cloud.countWrites(1)
        Unit
    }

    /**
     * Last week's result, resolved identically on every client: highest bid wins; ties go to the earliest bid, then
     * the lowest account id. The winner's own client applies the purchase once.
     */
    suspend fun lastResult() = cloud.cloud {
        val week = weekId(-1)
        val bids = db.collection("lots/$week/bids").get().await()
        cloud.countReads(bids.size().toLong().coerceAtLeast(1))
        data class B(val uid: String, val amount: java.math.BigDecimal, val at: Long)
        val all = bids.documents.mapNotNull { d -> d.getString("amount")?.let { B(d.id, java.math.BigDecimal(it), d.getTimestamp("createdAt")?.toDate()?.time ?: 0) } }
        val win = all.sortedWith(compareByDescending<B> { it.amount }.thenBy { it.at }.thenBy { it.uid }).firstOrNull()
        val item = lotItem(week)
        val iWon = win?.uid == me()
        if (iWon && !prefs.getBoolean("settled:$week", false)) {
            session.dispatch(Command.WinSealedLot(item, Money.ofDollars(win!!.amount)))
            prefs.edit().putBoolean("settled:$week", true).apply()
        }
        val name = win?.let { db.document("profiles/${it.uid}").get().await().getString("name") }
        LotResult(week, item, name, win?.let { Money.ofDollars(it.amount) }, iWon, all.size)
    }

    companion object {
        @Suppress("unused") private val ID = FieldPath.documentId()
    }
}
