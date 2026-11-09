package com.arnavgames.tycoon.game

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import java.math.BigInteger

/** Debug-only scenarios for screenshots and manual QA. Built only from real engine commands (no state hacking). */
internal object DebugScenarios {
    /** Left without managers in "lategame" so their hands-on controls can be screenshotted. */
    private val UNMANAGED = setOf("fashion", "energy", "bank", "airline")

    fun build(engine: GameEngine, base: GameState, name: String): GameState? = when (name) {
        "midgame" -> midGame(engine, base)
        "lategame" -> lateGame(engine, base)
        "markets" -> marketsOpen(engine, base)
        "auction" -> auctionClosing(engine, base)
        "world" -> worldDesk(engine, base)
        "crown" -> crownReady(engine, base)
        else -> null
    }

    private fun midGame(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", Money.ofDollars(3_000_000), 25, 40))
        listOf(
            Command.AdvanceOnboarding(5), Command.OpenBusiness("retail"), Command.BuyLevels("retail", 34),
            Command.HireManager("retail", "retail_mira"), Command.OpenBusiness("restaurants"), Command.BuyLevels("restaurants", 26),
            Command.SetRestaurantQuality("restaurants", "fine"), Command.OpenBusiness("logistics"), Command.BuyLevels("logistics", 21),
            Command.HireManager("restaurants", "rest_amara"), Command.StartExpansion("retail", "second_storefront"),
            Command.UpgradeHq, Command.UpgradeHq,
        ).forEach { s = s.run(it) }
        return engine.advance(s, 90_000).state
    }

    /**
     * Plays the story forward with a large grant: claims missions, opens and levels businesses, hires one manager
     * each, upgrades HQ and forms the first two conglomerates. Leaves the holding company unformed so its card shows.
     */
    private fun lateGame(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        // Real-world money (1.8.0): half a trillion dollars is a late-game fortune.
        val grant = Money.ofDollars(500_000_000_000)
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", grant, 500, 400))
        s = s.run(Command.AdvanceOnboarding(5))
        val content = engine.content
        repeat(40) { round ->
            s = s.run(Command.HustleTap(20))
            // The story asks for a first trade (chapter 2) and a board seat (chapter 3).
            if (s.market.trades == 0L) s = s.run(Command.BuyInstrument("fund_total", Money.ofDollars(1_000)))
            // ...luxury (three pieces in chapter 3) and an auction win in chapter 4 (by proxy)...
            if (s.progress.chapter >= 3 && s.luxury.owned.size < 3) {
                content.luxury.items.filter { engine.luxury.categoryUnlocked(s, it.category) && it.id !in s.luxury.owned }.sortedBy { it.price }.take(3 - s.luxury.owned.size)
                    .forEach { s = s.run(Command.BuyLuxury(it.id)) }
            }
            s.luxury.auction?.let { a ->
                if (s.progress.chapter >= 4 && s.luxury.auctionsWon == 0) s = s.run(Command.SetMaxBid(engine.luxury.marketPrice(content.luxury.itemById.getValue(a.item), s.simTimeMs) * 5L))
            }
            // ...and properties (one in chapter 2, five in chapter 4).
            val wantProps = if (s.progress.chapter >= 4) 5 else if (s.progress.chapter >= 2) 1 else 0
            while (s.estate.properties.size < wantProps) {
                val l = content.estates.districts.filter { it.city == "lisbon" }.flatMap { engine.estate.listings(s, it.id) }.minByOrNull { it.price } ?: break
                val before = s.estate.properties.size
                s = s.run(Command.BuyProperty(l.id))
                if (s.estate.properties.size == before) break
            }
            if (s.progress.chapter == 3 && engine.market.math.boardSeats(s).isEmpty()) {
                val st = content.markets.stocks.filter { it.sector == "consumer" }.minBy { it.baseCapBillions }
                val target = engine.market.math.outstanding(s, st.id).fraction(60, 1000).valueAt(engine.market.math.priceMicros(s, st.id)!!)
                s = s.run(Command.BuyInstrument(st.id, target * com.arnavgames.tycoon.core.money.Multiplier.of("1.1")))
            }
            content.chapters.flatMap { it.missions }.forEach { s = s.run(Command.ClaimMission(it.id)) }
            repeat(6) { s = s.run(Command.UpgradeHq) }
            content.businesses.forEach { b ->
                s = s.run(Command.OpenBusiness(b.id))
                s = s.run(Command.BuyLevels(b.id, 10))
                if (b.id !in UNMANAGED) b.managers.firstOrNull()?.let { m -> s = s.run(Command.HireManager(b.id, m.id)) }
                b.expansions.forEach { e -> s = s.run(Command.StartExpansion(b.id, e.id)) }
            }
            if (round > 20) content.conglomerates.take(2).forEach { s = s.run(Command.FormConglomerate(it.id)) }
            s = engine.advance(s, 30 * 60_000L).state
        }
        // A spread of positions so every Markets screen has something to show.
        val m = content.markets
        val math = engine.market.math
        val seat = m.stocks.filter { it.sector == "consumer" }.minBy { it.baseCapBillions }
        val seatShares = math.outstanding(s, seat.id).fraction(60, 1000)
        s = s.run(Command.BuyInstrument(seat.id, seatShares.valueAt(math.priceMicros(s, seat.id)!!) * com.arnavgames.tycoon.core.money.Multiplier.of("1.1")))
        s = s.run(Command.BuyInstrument("fund_total", s.cash * com.arnavgames.tycoon.core.money.Multiplier.of("0.0005")))
        s = s.run(Command.BuyInstrument("bond_gov_5", s.cash * com.arnavgames.tycoon.core.money.Multiplier.of("0.0003")))
        s = s.run(Command.BuyInstrument(m.coins[2].id, s.cash * com.arnavgames.tycoon.core.money.Multiplier.of("0.00002")))
        s = s.run(Command.BuyInstrument(m.stocks.first { it.sector == "technology" && it.band == "growth" }.id, s.cash * com.arnavgames.tycoon.core.money.Multiplier.of("0.00002")))
        s = s.run(Command.OpenShort(m.stocks.first { it.sector == "energy" }.id, s.cash * com.arnavgames.tycoon.core.money.Multiplier.of("0.00002")))
        // Real estate: a property in several cities (one mortgaged, one insured), a business at home, and a tower.
        val est = engine.estate
        for (city in listOf("lisbon", "london", "miami", "mumbai", "new_york", "dubai")) {
            val d = content.estates.districts.first { it.city == city }
            est.listings(s, d.id).firstOrNull()?.let { l -> s = s.run(Command.BuyProperty(l.id, if (city == "miami") 0.5 else 0.0, insured = city == "london")) }
        }
        val cbd = content.estates.districts.first { it.cbd && it.city == "dubai" }
        var t = s
        repeat(20) { if (est.listings(t, cbd.id).none { it.type == "land" }) t = engine.advance(t, 6 * 3_600_000L).state }
        s = t
        est.listings(s, cbd.id).firstOrNull { it.type == "land" }?.let { l ->
            s = s.run(Command.BuyProperty(l.id))
            val id = s.estate.nextPropertyId - 1
            repeat(2) { s = s.run(Command.BuildTower(id, content.estates.towers.first().id)); s = engine.advance(s, 9 * 3_600_000L).state }
        }
        // Luxury: a few pieces in every showroom.
        for (cat in content.luxury.categories) {
            content.luxury.items.filter { it.category == cat.id }.sortedByDescending { it.statusPoints }.take(3).forEach { s = s.run(Command.BuyLuxury(it.id)) }
        }
        content.estates.districts.firstOrNull { "retail_unit" in it.types && it.city == "london" }?.let { d ->
            est.listings(s, d.id).firstOrNull { it.type == "retail_unit" }?.let { l ->
                s = s.run(Command.BuyProperty(l.id))
                s = s.run(Command.HostBusiness(s.estate.nextPropertyId - 1, "retail"))
            }
        }
        return engine.advance(s, 90_000).state
    }

    /**
     * Chapter 1 finished with real commands and a modest grant, so the Markets tab has just opened (Chapter 2).
     * Used by the Markets UI test (TEST_PLAN flow 3) and for screenshots.
     */
    private fun marketsOpen(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", Money.ofDollars(2_000_000), 20, 60))
        s = s.run(Command.AdvanceOnboarding(5))
        val ch1 = engine.content.chapters.first { it.number == 1 }.missions
        repeat(30) {
            if (s.progress.chapter >= 2) return@repeat
            s = s.run(Command.HustleTap(30))
            ch1.forEach { s = s.run(Command.ClaimMission(it.id)) }
            s = s.run(Command.OpenBusiness("retail")).run(Command.BuyLevels("retail", 5))
            s = s.run(Command.HireManager("retail", engine.content.business("retail").managers.first().id))
            s = s.run(Command.OpenBusiness("restaurants")).run(Command.BuyLevels("restaurants", 3))
            s = s.run(Command.StartExpansion("retail", "market_stall"))
            s = engine.advance(s, 10 * 60_000L).state
        }
        // Savings for a first flat (real-world prices: a Lisbon apartment is about half a million dollars).
        s = s.copy(ledger = Grants.grant(s.ledger, s.simTimeMs, "debug scenario", Money.ofDollars(1_500_000), 0, 0))
        return engine.advance(s, 60_000).state
    }

    /**
     * Chapter 3 reached with real commands, an auction live with the player's proxy maximum set well above the
     * estimate, and the clock moved to 45 seconds before the hammer. Used by the auction UI test (TEST_PLAN flow 5).
     */
    private fun auctionClosing(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        val content = engine.content
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", Money.ofDollars(5_000_000_000), 100, 100))
        s = s.run(Command.AdvanceOnboarding(5))
        repeat(40) {
            if (s.progress.chapter >= 3) return@repeat
            s = s.run(Command.HustleTap(20))
            if (s.market.trades == 0L) s = s.run(Command.BuyInstrument("fund_total", Money.ofDollars(1_000)))
            if (s.progress.chapter >= 2 && s.estate.properties.isEmpty()) {
                content.estates.districts.filter { it.city == "lisbon" }.flatMap { engine.estate.listings(s, it.id) }.minByOrNull { it.price }?.let { l -> s = s.run(Command.BuyProperty(l.id)) }
            }
            content.chapters.flatMap { it.missions }.forEach { s = s.run(Command.ClaimMission(it.id)) }
            repeat(4) { s = s.run(Command.UpgradeHq) }
            content.businesses.forEach { b ->
                s = s.run(Command.OpenBusiness(b.id)).run(Command.BuyLevels(b.id, 10))
                b.managers.firstOrNull()?.let { m -> s = s.run(Command.HireManager(b.id, m.id)) }
                b.expansions.forEach { e -> s = s.run(Command.StartExpansion(b.id, e.id)) }
            }
            s = engine.advance(s, 30 * 60_000L).state
        }
        var guard = 0
        while (s.luxury.auction == null && guard++ < 300) s = engine.advance(s, 60_000).state
        val a = s.luxury.auction ?: return s
        s = s.run(Command.SetMaxBid(engine.luxury.marketPrice(content.luxury.itemById.getValue(a.item), s.simTimeMs) * 10L))
        val target = (s.luxury.auction?.closeSim ?: s.simTimeMs) - 45_000
        if (target > s.simTimeMs) s = engine.advance(s, target - s.simTimeMs).state
        return s
    }

    /**
     * The late game played forward through a full tax quarter (72 game hours, real engine time): decision cards are
     * waiting on the Desk, a tax return is due, world events have happened and a loan is running. Used by the Desk
     * and tax UI test (TEST_PLAN flow 6).
     */
    private fun worldDesk(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        var s = lateGame(engine, base)
        s = engine.advance(s, 60_000).state
        // Play on in 3-hour steps until a tax return is open (at most two quarters).
        var guard = 0
        while (s.finance.tax.pending == null && guard++ < 48) s = engine.advance(s, 3 * 3_600_000L).state
        engine.credit.borrowLimit(s).takeIf { it.isPositive }?.let { lim -> s = s.run(Command.TakeLoan(lim / 4L, 3)) }
        return s
    }

    /**
     * The late game played on to the end of chapter 8 (real commands: missions, conglomerates, the last businesses,
     * levels), so the founder can pass the crown. Used by the Dynasty prestige UI test (TEST_PLAN flow 7).
     */
    private fun crownReady(engine: GameEngine, base: GameState): GameState {
        fun GameState.run(c: Command) = (engine.apply(this, c) as? CommandResult.Ok)?.state ?: this
        val content = engine.content
        var s = lateGame(engine, base)
        // The finale needs every business at Scale Tier 2 and a $270B fortune: top up to an end-game sum.
        s = s.copy(ledger = Grants.grant(s.ledger, s.simTimeMs, "debug scenario", Money.ofDollars(3_000_000_000_000), 0, 0))
        var guard = 0
        while (!engine.dynasty.canPrestige(s) && guard++ < 40) {
            content.chapters.flatMap { it.missions }.forEach { s = s.run(Command.ClaimMission(it.id)) }
            content.businesses.forEach { b ->
                // Slots are full late in the game: mothball the weakest business outside a conglomerate to make room.
                if (b.id !in s.businesses && engine.economy.usedSlots(s) >= engine.economy.slots(s)) {
                    s.businesses.values.filter { !it.mothballed && it.managerId != null && engine.economy.conglomerateOf(s, it.id) == null }
                        .minByOrNull { engine.economy.businessRate(s, it.id).net.perSecond() }?.let { w -> s = s.run(Command.SetMothballed(w.id, true)) }
                }
                s = s.run(Command.OpenBusiness(b.id))
            }
            // Chapters 5-8 ask for a manager on each new business (hired before levels spend the cash).
            content.businesses.forEach { b -> if (s.businesses[b.id]?.managerId == null) b.managers.firstOrNull()?.let { m -> s = s.run(Command.HireManager(b.id, m.id)) } }
            // Levels last, so opening the newest (most expensive) businesses is never starved of cash; and none while
            // a manager is still missing (a first manager costs 13× its business's opening price).
            val wanted = content.chapters.flatMap { it.missions }.mapNotNull { (it.goal as? com.arnavgames.tycoon.core.content.GoalDef.HireManager)?.businessId }.toSet()
            val managerMissing = s.businesses.values.any { it.id in wanted && it.managerId == null && !it.mothballed }
            // Buy toward what the finale asks for (Scale Tier 3 on the early businesses, Tier 2 on the big ones), newest
            // first, rather than maxing the small shops (at real prices their high levels would swallow the fortune).
            if (!managerMissing) content.businesses.sortedByDescending { it.chapter }.forEach { b ->
                val target = if (b.chapter <= 3) 50 else 25
                val have = s.businesses[b.id]?.level ?: return@forEach
                if (have < target) s = s.run(Command.BuyLevels(b.id, target - have))
            }
            content.conglomerates.forEach { s = s.run(Command.FormConglomerate(it.id)) }
            s = engine.advance(s, 30 * 60_000L).state
        }
        content.chapters.flatMap { it.missions }.forEach { s = s.run(Command.ClaimMission(it.id)) }
        return s
    }
}
