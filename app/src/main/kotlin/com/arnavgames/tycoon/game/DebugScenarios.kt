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
        val grant = Money.ofCents(BigInteger.TEN.pow(26))
        var s = base.copy(ledger = Grants.grant(base.ledger, base.simTimeMs, "debug scenario", grant, 500, 400))
        s = s.run(Command.AdvanceOnboarding(5))
        val content = engine.content
        repeat(40) { round ->
            s = s.run(Command.HustleTap(20))
            // The story asks for a first trade (chapter 2) and a board seat (chapter 3).
            if (s.market.trades == 0L) s = s.run(Command.BuyInstrument("fund_total", Money.ofDollars(1_000)))
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
        return engine.advance(s, 60_000).state
    }
}
