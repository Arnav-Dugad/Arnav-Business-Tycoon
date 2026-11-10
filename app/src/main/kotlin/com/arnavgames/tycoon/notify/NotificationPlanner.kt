package com.arnavgames.tycoon.notify

import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.state.GameState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What a notification is about; each has its own switch in Settings. */
enum class NotifyCategory { Timers, Auctions, Desk }

data class NotifyPrefs(
    val timers: Boolean = true,
    val auctions: Boolean = true,
    val desk: Boolean = true,
    /** Nothing between [quietStartHour] and [quietEndHour]; anything due then waits for the morning. */
    val quietHours: Boolean = true,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
) {
    fun allows(c: NotifyCategory) = when (c) {
        NotifyCategory.Timers -> timers
        NotifyCategory.Auctions -> auctions
        NotifyCategory.Desk -> desk
    }
}

data class PlannedNotification(val atWallMs: Long, val category: NotifyCategory, val title: String, val body: String)

/**
 * Works out the local notifications for one absence (docs/PLAN_2.0.md E6). The engine is deterministic, so the
 * planner simply plays the coming hours forward from the saved game, exactly as the offline credit will when the
 * player returns, and turns real events into messages. Nothing is guessed, and nothing is sent from a server.
 *
 * Only what happens inside the offline cap can happen at all (time stops counting after it), so that bounds the
 * look-ahead. Rules: per-category switches, quiet hours, events close together merged into one message, and at
 * most [MAX_PER_DAY] a day.
 */
object NotificationPlanner {
    const val STEP_MS = 5 * 60_000L
    const val MERGE_MS = 15 * 60_000L
    const val MAX_PER_DAY = 3
    private const val MIN_LEAD_MS = 60_000L

    internal data class Raw(val offsetMs: Long, val category: NotifyCategory, val title: String, val body: String)

    fun plan(engine: GameEngine, start: GameState, nowWall: Long, capMs: Long, prefs: NotifyPrefs, zone: ZoneId = ZoneId.systemDefault()): List<PlannedNotification> =
        arrange(simulate(engine, start, capMs), nowWall, prefs, zone)

    /** Category switches, quiet hours, merging and the daily limit, applied to what the simulation found. */
    internal fun arrange(found: List<Raw>, nowWall: Long, prefs: NotifyPrefs, zone: ZoneId): List<PlannedNotification> {
        val raw = found.filter { prefs.allows(it.category) && it.offsetMs >= MIN_LEAD_MS }
        // Quiet hours push a message to the end of the quiet spell.
        val timed = raw.map { r -> r to deferQuiet(nowWall + r.offsetMs, prefs, zone) }.sortedBy { it.second }
        // Merge anything due within MERGE_MS of the first message of a group.
        val merged = mutableListOf<PlannedNotification>()
        var i = 0
        while (i < timed.size) {
            val at = timed[i].second
            val group = timed.drop(i).takeWhile { it.second - at <= MERGE_MS }.map { it.first }
            merged += if (group.size == 1) {
                PlannedNotification(at, group[0].category, group[0].title, group[0].body)
            } else {
                PlannedNotification(at, group[0].category, "${group.size} updates from your empire", group.joinToString(" · ") { it.title })
            }
            i += group.size
        }
        // At most MAX_PER_DAY per calendar day.
        val perDay = mutableMapOf<LocalDate, Int>()
        return merged.filter { n ->
            val day = Instant.ofEpochMilli(n.atWallMs).atZone(zone).toLocalDate()
            val count = perDay.getOrDefault(day, 0)
            (count < MAX_PER_DAY).also { if (it) perDay[day] = count + 1 }
        }
    }

    private fun deferQuiet(atWall: Long, prefs: NotifyPrefs, zone: ZoneId): Long {
        if (!prefs.quietHours) return atWall
        val t = Instant.ofEpochMilli(atWall).atZone(zone)
        val h = t.hour
        val inQuiet = if (prefs.quietStartHour > prefs.quietEndHour) h >= prefs.quietStartHour || h < prefs.quietEndHour
        else h >= prefs.quietStartHour && h < prefs.quietEndHour
        if (!inQuiet) return atWall
        var end = t.toLocalDate().atTime(prefs.quietEndHour, 0).atZone(zone)
        if (!end.isAfter(t)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    internal fun simulate(engine: GameEngine, start: GameState, capMs: Long): List<Raw> {
        val c = engine.content
        val out = mutableListOf<Raw>()
        val deskMax = engine.desk.maxQueued(start)
        var deskFull = start.world.cards.size >= deskMax // already full when leaving: they know
        // Only auctions the player has a stake in: their maximum bid, or currently leading.
        val stake = start.luxury.auction?.takeIf { it.playerMax.isPositive || it.leader == "player" }?.item
        var s = start
        var t = 0L
        while (t < capMs) {
            val dt = minOf(STEP_MS, capMs - t)
            val r = engine.advance(s, dt)
            s = r.state
            t += dt
            for (e in r.events) when (e) {
                is GameEvent.ExpansionCompleted -> {
                    val def = c.businessById[e.businessId]
                    val name = def?.expansions?.firstOrNull { it.id == e.expansionId }?.name ?: "Your expansion"
                    out += Raw(t, NotifyCategory.Timers, "$name is open", "${def?.name ?: "Your business"} earns more from now on.")
                }
                is GameEvent.RenovationCompleted -> {
                    val where = s.estate.properties[e.propertyId]?.let { p -> c.estates.districtById[p.district]?.name }
                    out += Raw(t, NotifyCategory.Timers, "Renovation finished", "Your property${where?.let { " in $it" } ?: ""} is ready to earn more rent.")
                }
                is GameEvent.TowerCompleted -> out += Raw(t, NotifyCategory.Timers, "Your tower is complete", "The ribbon is cut. Come and see your skyline.")
                is GameEvent.TowerStageCompleted -> out += Raw(t, NotifyCategory.Timers, "A construction stage finished", "Start the next stage when you're ready.")
                is GameEvent.AuctionWon -> if (e.itemId == stake) {
                    val name = c.luxury.itemById[e.itemId]?.name ?: "the lot"
                    out += Raw(t, NotifyCategory.Auctions, "Hammer falls: $name is yours", "Your agent won it for ${MoneyFormat.compact(e.price)}.")
                }
                is GameEvent.AuctionLost -> if (e.itemId == stake) {
                    val name = c.luxury.itemById[e.itemId]?.name ?: "the lot"
                    out += Raw(t, NotifyCategory.Auctions, "Outbid on $name", "${e.winner} went higher than your maximum. A new sale opens every six hours.")
                }
                else -> Unit
            }
            if (!deskFull && s.world.cards.size >= deskMax) {
                deskFull = true
                out += Raw(t, NotifyCategory.Desk, "Your Desk is full", "${s.world.cards.size} decisions are waiting. New ones pause until you decide.")
            }
        }
        return out
    }
}
