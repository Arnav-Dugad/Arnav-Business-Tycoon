package com.arnavgames.tycoon.core.luxury

import com.arnavgames.tycoon.core.content.LuxuryContent
import com.arnavgames.tycoon.core.content.LuxuryItemDef
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.AuctionBidder
import com.arnavgames.tycoon.core.state.AuctionState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.OwnedItem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Luxury, Status and auctions (docs/GDD.md §8, docs/ECONOMY.md §7). Item values, the art cycle and auction rivals are
 * deterministic in the world seed and time; auctions run on the engine's event boundaries, so offline play resolves
 * them exactly as live play would (without the player bidding).
 */
class LuxuryEngine(val c: LuxuryContent, private val extraBonus: (GameState, String) -> Double = { _, _ -> 0.0 }) {
    private val cfg = c.config

    fun isUnlocked(state: GameState, feature: String) = state.progress.chapter >= (cfg.unlocks[feature] ?: 99)
    fun unlockChapter(feature: String) = cfg.unlocks[feature] ?: 99
    fun categoryUnlocked(state: GameState, categoryId: String) =
        isUnlocked(state, "collection") && state.progress.chapter >= (c.categoryById[categoryId]?.chapter ?: 99)

    // =====================================================================================
    // Values and Status
    // =====================================================================================

    private fun days(ms: Long) = ms / 86_400_000.0

    private fun artCycle(item: LuxuryItemDef, simMs: Long): Double {
        val cat = c.categoryById.getValue(item.category)
        if (!cat.artCycle) return 1.0
        val phase = (item.id.hashCode() and 0xFFFF) / 65535.0 * 2 * PI
        return 1 + cfg.artCycleAmplitude * sin(2 * PI * days(simMs) / cfg.artCyclePeriodDays + phase)
    }

    /** What a dealer asks today (before Status and set discounts). */
    fun marketPrice(item: LuxuryItemDef, simMs: Long): Money = item.price * Multiplier.ofDouble(artCycle(item, simMs))

    fun dealerDiscount(state: GameState): Double = (tier(state).dealerDiscount + setBonus(state, "luxuryDiscount") + extraBonus(state, "luxuryDiscount")).coerceAtMost(0.4)

    fun dealerPrice(state: GameState, item: LuxuryItemDef): Money =
        marketPrice(item, state.simTimeMs) * Multiplier.ofDouble(1 - dealerDiscount(state))

    /** Value of an owned item: market price drifted by its category (cars depreciate, classics and wine appreciate). */
    fun ownedValue(state: GameState, itemId: String, simMs: Long = state.simTimeMs): Money {
        val item = c.itemById[itemId] ?: return Money.ZERO
        val o = state.luxury.owned[itemId] ?: return Money.ZERO
        val drift = if (item.classic) 0.003 else c.categoryById.getValue(item.category).driftPerDay
        return marketPrice(item, simMs) * Multiplier.ofDouble(exp(drift * days(simMs - o.boughtAtSim)))
    }

    fun collectionValue(state: GameState): Money = state.luxury.owned.keys.fold(Money.ZERO) { a, id -> a + ownedValue(state, id) }

    fun capacity(state: GameState, showroomId: String): Int {
        val s = c.showroomById[showroomId] ?: return 0
        val lvl = state.luxury.showroomLevels[showroomId] ?: 0
        return (s.baseCapacity * (1 + cfg.showroomCapacityStep * lvl)).toInt()
    }

    fun upgradeCost(state: GameState, showroomId: String): Money {
        val lvl = state.luxury.showroomLevels[showroomId] ?: 0
        return cfg.showroomUpgradeBase * Multiplier.ofDouble(Math.pow(cfg.showroomUpgradeGrowth, lvl.toDouble()))
    }

    /** Items on display: the highest-Status items in each showroom, up to its capacity. Only displayed items earn Status. */
    fun displayed(state: GameState): Set<String> {
        val out = HashSet<String>()
        for (room in c.showrooms) {
            state.luxury.owned.keys.mapNotNull { c.itemById[it] }
                .filter { c.categoryById.getValue(it.category).showroom == room.id }
                .sortedWith(compareByDescending<LuxuryItemDef> { it.statusPoints }.thenBy { it.id })
                .take(capacity(state, room.id))
                .forEach { out += it.id }
        }
        return out
    }

    fun statusPoints(state: GameState): Double =
        displayed(state).sumOf { c.itemById.getValue(it).statusPoints } * (1 + setBonus(state, "status") + extraBonus(state, "status"))

    fun tierIndex(state: GameState): Int {
        val pts = statusPoints(state)
        return c.tiers.indexOfLast { pts >= it.points }.coerceAtLeast(0)
    }

    fun tier(state: GameState) = c.tiers.getOrNull(tierIndex(state)) ?: com.arnavgames.tycoon.core.content.StatusTierDef("Newcomer", 0, 0.0)

    fun completedSets(state: GameState) = c.sets.filter { s -> s.items.all { it in state.luxury.owned } }

    // Set bonuses are read from businessRate (the hottest path), so they are memoised on the owned map instance.
    private class SetMemo(val owned: Map<String, *>, val bonus: Map<String, Double>, val income: Map<String, Double>)
    @Volatile private var memo: SetMemo? = null

    private fun bonuses(state: GameState): SetMemo {
        val m = memo
        if (m != null && m.owned === state.luxury.owned) return m
        val bonus = HashMap<String, Double>()
        val income = HashMap<String, Double>()
        for (s in completedSets(state)) {
            if (s.bonusType == "income") income[s.bonusTarget] = (income[s.bonusTarget] ?: 0.0) + s.bonusValue
            else bonus[s.bonusType] = (bonus[s.bonusType] ?: 0.0) + s.bonusValue
        }
        return SetMemo(state.luxury.owned, bonus, income).also { memo = it }
    }

    fun setBonus(state: GameState, type: String): Double = if (state.luxury.owned.isEmpty()) 0.0 else bonuses(state).bonus[type] ?: 0.0

    fun incomeBonus(state: GameState, businessId: String): Double = if (state.luxury.owned.isEmpty()) 0.0 else bonuses(state).income[businessId] ?: 0.0

    // =====================================================================================
    // Dealer and showrooms
    // =====================================================================================

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    private fun err(e: GameError) = Result.Err(e)

    private fun post(state: GameState, memo: String, postings: List<Posting>, floors: Map<String, Money> = Ledger.DEFAULT_FLOORS) =
        when (val r = Ledger.post(state.ledger, state.simTimeMs, TxnCategory.Luxury, memo, postings, floors)) {
            is Ledger.Result.Ok -> r.state to null
            is Ledger.Result.Err -> null to when (val e = r.error) { is LedgerError.InsufficientFunds -> GameError.InsufficientFunds(e.needed, e.available) }
        }

    fun buyCost(state: GameState, item: LuxuryItemDef): Money = dealerPrice(state, item) * Multiplier.ofDouble(1 + cfg.dealerFee)

    fun buy(state: GameState, itemId: String, events: MutableList<GameEvent>): Result {
        val item = c.itemById[itemId] ?: return err(GameError.NotFound("item"))
        if (!categoryUnlocked(state, item.category)) return err(GameError.Locked("${c.categoryById.getValue(item.category).name} open in chapter ${c.categoryById.getValue(item.category).chapter}"))
        if (itemId in state.luxury.owned) return err(GameError.AlreadyDone)
        if (state.luxury.auction?.item == itemId) return err(GameError.Invalid("It's at auction right now"))
        val price = dealerPrice(state, item)
        val fee = price * Multiplier.ofDouble(cfg.dealerFee)
        val (ledger, e) = post(state, "buy ${item.maker} ${item.name}", listOf(Posting(Accounts.CASH, -(price + fee)), Posting(LUXURY, price), Posting(Accounts.expense("luxury_fees"), fee)))
        if (ledger == null) return err(e!!)
        events += GameEvent.LuxuryBought(itemId, price)
        return Result.Ok(state.copy(ledger = ledger, luxury = state.luxury.copy(owned = state.luxury.owned + (itemId to OwnedItem(price, state.simTimeMs)))))
    }

    fun sellQuote(state: GameState, itemId: String): Money = ownedValue(state, itemId) * Multiplier.ofDouble(cfg.dealerBuyback)

    fun sell(state: GameState, itemId: String, events: MutableList<GameEvent>): Result {
        val o = state.luxury.owned[itemId] ?: return err(GameError.NotFound("You don't own that"))
        val proceeds = sellQuote(state, itemId)
        val (ledger, e) = post(state, "sell ${c.itemById.getValue(itemId).name}", listOf(
            Posting(Accounts.CASH, proceeds), Posting(LUXURY, -o.paid), Posting(Accounts.revenue("luxury_gains"), -(proceeds - o.paid)),
        ), emptyMap())
        if (ledger == null) return err(e!!)
        events += GameEvent.LuxurySold(itemId, proceeds)
        return Result.Ok(state.copy(ledger = ledger, luxury = state.luxury.copy(owned = state.luxury.owned - itemId, heirlooms = state.luxury.heirlooms - itemId)))
    }

    fun upgradeShowroom(state: GameState, showroomId: String): Result {
        if (showroomId !in c.showroomById) return err(GameError.NotFound("showroom"))
        if (!isUnlocked(state, "collection")) return err(GameError.Locked("Collection opens in chapter ${unlockChapter("collection")}"))
        val cost = upgradeCost(state, showroomId)
        val (ledger, e) = post(state, "upgrade ${c.showroomById.getValue(showroomId).name}", listOf(Posting(Accounts.CASH, -cost), Posting(Accounts.expense("showrooms"), cost)))
        if (ledger == null) return err(e!!)
        val lvl = (state.luxury.showroomLevels[showroomId] ?: 0) + 1
        return Result.Ok(state.copy(ledger = ledger, luxury = state.luxury.copy(showroomLevels = state.luxury.showroomLevels + (showroomId to lvl))))
    }

    // =====================================================================================
    // Auctions
    // =====================================================================================

    fun slotOf(minute: Long) = minute / (cfg.auctionEveryHours * 60L)

    /**
     * The last scheduled slot, or -1 if it lies in the future: saves from before 1.8.1 counted 2-hour slots, and a
     * longer interval renumbers them, which would otherwise pause auctions until the new numbering caught up.
     */
    private fun lastSlot(state: GameState, slot: Long) = state.luxury.lastAuctionSlot.let { if (it > slot) -1L else it }

    /**
     * Each auction starts at a seeded moment inside its slot (0–75 min after the slot begins), so a player with a fixed
     * daily routine still catches auctions instead of always arriving just after one closes.
     */
    fun startMinute(seed: Long, slot: Long): Long =
        slot * cfg.auctionEveryHours * 60L + GameRandom.stream(seed, "auction_start", slot).nextInt(0, 76)

    fun nextAuctionAt(state: GameState): Long {
        val minute = state.simTimeMs / 60_000
        val slot = slotOf(minute)
        val thisStart = startMinute(state.worldSeed, slot)
        val next = if (slot > lastSlot(state, slot) && minute < thisStart) thisStart else startMinute(state.worldSeed, slot + 1)
        return next * 60_000L
    }

    fun nextBidIncrement(a: AuctionState, item: LuxuryItemDef): Money =
        if (a.leader.isEmpty()) a.bid else a.bid * Multiplier.ofDouble(1 + cfg.auctionBidIncrement)

    private fun startAuction(state: GameState, slot: Long, events: MutableList<GameEvent>): GameState {
        val r = GameRandom.stream(state.worldSeed, "auction", slot)
        // Auctions are for premium lots: rare or legendary pieces worth at least $250K.
        val pool = c.items.filter { it.rarity != "common" && it.price >= Money.ofDollars(250_000) && it.id !in state.luxury.owned && categoryUnlocked(state, it.category) }
        if (pool.isEmpty()) return state.copy(luxury = state.luxury.copy(lastAuctionSlot = slot))
        // The house invites you to sales in your league: lots up to 20 % of your peak net worth and half your cash, so
        // you can actually bid (a fortune tied up in businesses is no use in a saleroom). Else the cheapest few.
        val league = pool.filter {
            val price = it.price.toDollarsDouble()
            price <= 0.2 * state.stats.peakNetWorth.toDollarsDouble() && price <= 0.5 * state.cash.toDollarsDouble()
        }
            .ifEmpty { pool.sortedBy { it.price }.take(5) }
        val item = league[r.nextInt(league.size)]
        val value = marketPrice(item, state.simTimeMs)
        val n = r.nextInt(cfg.auctionBiddersMin, cfg.auctionBiddersMax + 1)
        val chosen = r.shuffled(c.bidders).take(n)
        val start = state.simTimeMs
        val close = start + cfg.auctionMinSeconds * 1000L
        val bidders = chosen.map { b ->
            val styleFactor = when (b.style) { "aggressive" -> 1.05; "collector" -> if (c.categoryById.getValue(item.category).artCycle) 1.15 else 1.0; "sniper" -> 1.0; else -> 0.95 }
            val valuation = (1.0 + r.nextGaussian() * cfg.auctionValuationSd).coerceIn(0.6, 1.6) * styleFactor
            AuctionBidder(b.id, value * Multiplier.ofDouble(valuation), firstBidAt(r.nextDouble(), b.style, start, close))
        }
        events += GameEvent.AuctionStarted(item.id)
        val a = AuctionState(slot, item.id, start, close, value * Multiplier.ofDouble(cfg.auctionOpeningShare), "", bidders, log = listOf("Opening bid set"))
        return state.copy(luxury = state.luxury.copy(auction = a, lastAuctionSlot = slot))
    }

    // Rivals work on a slow rhythm while the auction is open (minutes) and speed up in the final seconds.
    private fun firstBidAt(u: Double, style: String, start: Long, close: Long): Long = when (style) {
        "sniper" -> close - 12_000L
        "aggressive" -> start + (60_000 + u * 240_000).toLong()
        "collector" -> start + (180_000 + u * 720_000).toLong()
        else -> start + (300_000 + u * 900_000).toLong()
    }

    private fun delay(u: Double, style: String, now: Long, close: Long): Long {
        val endgame = close - now <= 60_000
        return when (style) {
            "sniper" -> if (close - now > 20_000) close - 12_000 else now + (1_500 + u * 2_500).toLong()
            "aggressive" -> now + if (endgame) (3_000 + u * 5_000).toLong() else (30_000 + u * 60_000).toLong()
            "collector" -> now + if (endgame) (6_000 + u * 9_000).toLong() else (60_000 + u * 180_000).toLong()
            else -> now + if (endgame) (8_000 + u * 12_000).toLong() else (120_000 + u * 240_000).toLong()
        }
    }

    /** The earliest auction event after [now] (a rival's next bid or the hammer), for the engine's boundary search. */
    fun nextEvent(state: GameState, now: Long): Long? {
        val a = state.luxury.auction ?: return null
        var t = a.closeSim
        for (b in a.bidders) if (!b.out && b.id != a.leader && b.nextBidAt in (now + 1)..<t) t = b.nextBidAt
        return t
    }

    /** Applies rival bids and the hammer that fall exactly at [now]. */
    fun onBoundary(state: GameState, now: Long, events: MutableList<GameEvent>): GameState {
        var a = state.luxury.auction ?: return state
        val s0 = state
        var s = state
        val item = c.itemById.getValue(a.item)
        // Rival bids due now (in a stable order).
        for (idx in a.bidders.indices) {
            val b = a.bidders[idx]
            if (b.out || b.id == a.leader || b.nextBidAt != now || now >= a.closeSim) continue
            val needed = nextBidIncrement(a, item)
            a = if (needed <= b.maxBid) {
                val close = maxOf(a.closeSim, now + cfg.auctionExtendSeconds * 1000L)
                val bidder = c.bidderById.getValue(b.id)
                val withBid = a.copy(bid = needed, leader = b.id, bidCount = a.bidCount + 1, closeSim = close, log = (a.log + "${bidder.name} bids").takeLast(12))
                proxyCounter(s0, reschedule(state.worldSeed, withBid, now), now)
            } else {
                a.copy(bidders = a.bidders.mapIndexed { i, x -> if (i == idx) x.copy(out = true) else x }, log = (a.log + "${c.bidderById.getValue(b.id).name} drops out").takeLast(12))
            }
        }
        s = s.copy(luxury = s.luxury.copy(auction = a))
        if (now >= a.closeSim) s = hammer(s, a, events)
        return s
    }

    /** If the player left a maximum bid that covers the next step, the game bids for them straight away. */
    private fun proxyCounter(state: GameState, a: AuctionState, now: Long): AuctionState {
        if (a.leader == PLAYER || a.playerMax.isZero) return a
        val needed = nextBidIncrement(a, c.itemById.getValue(a.item))
        if (needed > a.playerMax || state.cash < needed) return a
        val close = maxOf(a.closeSim, now + cfg.auctionExtendSeconds * 1000L)
        return reschedule(state.worldSeed, a.copy(bid = needed, leader = PLAYER, bidCount = a.bidCount + 1, closeSim = close, log = (a.log + PLAYER_BID).takeLast(12)), now)
    }

    /** Sets (or raises) the player's proxy maximum and bids immediately if they are not leading. */
    fun setMaxBid(state: GameState, max: Money, events: MutableList<GameEvent>): Result {
        val a = state.luxury.auction ?: return err(GameError.NotFound("No auction right now"))
        if (!isUnlocked(state, "auctions")) return err(GameError.Locked("Auctions open in chapter ${unlockChapter("auctions")}"))
        if (state.simTimeMs >= a.closeSim) return err(GameError.Invalid("Bidding has closed"))
        val needed = nextBidIncrement(a, c.itemById.getValue(a.item))
        if (a.leader != PLAYER && max < needed) return err(GameError.Invalid("Your maximum must be at least the next bid"))
        var na = a.copy(playerMax = max)
        if (na.leader != PLAYER) {
            if (state.cash < needed) return err(GameError.InsufficientFunds(needed, state.cash))
            na = proxyCounter(state, na, state.simTimeMs)
            events += GameEvent.AuctionBid(a.item, na.bid)
        }
        return Result.Ok(state.copy(luxury = state.luxury.copy(auction = na)))
    }

    private fun reschedule(seed: Long, a: AuctionState, now: Long): AuctionState {
        val r = GameRandom.stream(seed, "auction:${a.slot}", a.bidCount.toLong())
        return a.copy(bidders = a.bidders.map { b ->
            val u = r.nextDouble()
            if (b.out || b.id == a.leader) b else b.copy(nextBidAt = delay(u, c.bidderById.getValue(b.id).style, now, a.closeSim))
        })
    }

    private fun hammer(state: GameState, a: AuctionState, events: MutableList<GameEvent>): GameState {
        val item = c.itemById.getValue(a.item)
        var lux = state.luxury.copy(auction = null)
        var ledger = state.ledger
        val playerTookPart = a.log.any { it == PLAYER_BID }
        if (a.leader == PLAYER) {
            val r = Ledger.post(ledger, state.simTimeMs, TxnCategory.Luxury, "auction: ${item.name}", listOf(Posting(Accounts.CASH, -a.bid), Posting(LUXURY, a.bid)))
            if (r is Ledger.Result.Ok) {
                ledger = r.state
                lux = lux.copy(owned = lux.owned + (item.id to OwnedItem(a.bid, state.simTimeMs, viaAuction = true)), auctionsWon = lux.auctionsWon + 1)
                events += GameEvent.AuctionWon(item.id, a.bid)
            } else {
                lux = lux.copy(auctionsLost = lux.auctionsLost + 1)
                events += GameEvent.AuctionLost(item.id, "")
            }
        } else {
            if (playerTookPart) {
                lux = lux.copy(auctionsLost = lux.auctionsLost + 1)
                events += GameEvent.AuctionLost(item.id, c.bidderById[a.leader]?.name ?: "")
            }
        }
        return state.copy(ledger = ledger, luxury = lux)
    }

    fun bid(state: GameState, events: MutableList<GameEvent>): Result {
        val a = state.luxury.auction ?: return err(GameError.NotFound("No auction right now"))
        if (!isUnlocked(state, "auctions")) return err(GameError.Locked("Auctions open in chapter ${unlockChapter("auctions")}"))
        if (a.leader == PLAYER) return err(GameError.Invalid("You're already the highest bidder"))
        if (state.simTimeMs >= a.closeSim) return err(GameError.Invalid("Bidding has closed"))
        val needed = nextBidIncrement(a, c.itemById.getValue(a.item))
        if (state.cash < needed) return err(GameError.InsufficientFunds(needed, state.cash))
        val close = maxOf(a.closeSim, state.simTimeMs + cfg.auctionExtendSeconds * 1000L)
        val na = reschedule(state.worldSeed, a.copy(bid = needed, leader = PLAYER, bidCount = a.bidCount + 1, closeSim = close, log = (a.log + PLAYER_BID).takeLast(12)), state.simTimeMs)
        events += GameEvent.AuctionBid(a.item, needed)
        return Result.Ok(state.copy(luxury = state.luxury.copy(auction = na)))
    }

    // =====================================================================================
    // Minute tick: auctions start on schedule; upkeep every 6 hours
    // =====================================================================================

    fun tick(state: GameState, minute: Long, events: MutableList<GameEvent>): GameState {
        if (c.items.isEmpty() || minute <= state.luxury.tick) return state
        var s = state.copy(luxury = state.luxury.copy(tick = minute))
        val slot = slotOf(minute)
        if (isUnlocked(s, "auctions") && s.luxury.auction == null && slot > lastSlot(s, slot) && minute >= startMinute(s.worldSeed, slot)) s = startAuction(s, slot, events)
        if (minute % (6 * 60) == 0L && s.luxury.owned.isNotEmpty()) {
            var upkeep = Money.ZERO
            for (id in s.luxury.owned.keys.sorted()) {
                val perDay = c.categoryById.getValue(c.itemById.getValue(id).category).upkeepPerDay
                if (perDay > 0) upkeep += ownedValue(s, id) * Multiplier.ofDouble(perDay / 4)
            }
            val paid = upkeep.min(s.cash.max(Money.ZERO))
            if (paid.isPositive) {
                val r = Ledger.post(s.ledger, minute * 60_000, TxnCategory.Luxury, "crew, berths and hangars", listOf(Posting(Accounts.CASH, -paid), Posting(Accounts.expense("luxury_upkeep"), paid)))
                if (r is Ledger.Result.Ok) s = s.copy(ledger = r.state)
            }
        }
        return s
    }

    companion object {
        const val LUXURY = "asset:luxury"
        const val PLAYER = "player"
        const val PLAYER_BID = "You bid"
    }
}
