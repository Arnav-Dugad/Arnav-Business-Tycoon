package com.arnavgames.tycoon.core.estate

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withCash
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.TimerKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EstateTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val est = engine.estate
    private val c = engine.content.estates

    private fun atChapter(s: GameState, ch: Int) = s.copy(progress = s.progress.copy(chapter = ch))
    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")
    private val harbour = c.districts.first { it.city == "lisbon" }.id

    /** Buys the cheapest listing in a district and returns the state and new property id. */
    private fun buyCheapest(s: GameState, district: String = harbour, ltv: Double = 0.0, insured: Boolean = false, distressed: Boolean? = null): Pair<GameState, Long> {
        val l = est.listings(s, district).filter { distressed == null || it.distressed == distressed }.minBy { it.price }
        val ns = s.run(Command.BuyProperty(l.id, ltv, insured))
        return ns to (ns.estate.nextPropertyId - 1)
    }

    @Test
    fun priceIndexIsDeterministicAndBounded() {
        val s = newGame(1)
        for (d in c.districts) {
            val a = est.priceIndex(s, d.id, 5 * day)
            assertEquals(a, est.priceIndex(s, d.id, 5 * day))
            assertTrue(a in 0.3..3.0, "${d.id} index $a")
        }
    }

    @Test
    fun listingsAreDeterministicPerEpochAndVanishWhenBought() {
        val s = atChapter(newGame(2).withCash(10_000_000), 2)
        val a = est.listings(s, harbour)
        assertEquals(a, est.listings(s, harbour))
        assertTrue(a.size in c.config.listingsMin..c.config.listingsMax)
        val (s2, _) = buyCheapest(s)
        assertEquals(a.size - 1, est.listings(s2, harbour).size)
        // A new epoch brings fresh listings.
        val later = s2.adv(7 * hour)
        assertNotEquals(a.map { it.id }, est.listings(later, harbour).map { it.id })
    }

    @Test
    fun buyingPostsCostsAndCountsInNetWorth() {
        val s0 = atChapter(newGame(3).withCash(10_000_000), 2)
        val l = est.listings(s0, harbour).minBy { it.price }
        val s1 = s0.run(Command.BuyProperty(l.id))
        trial(s1)
        val fee = l.price * com.arnavgames.tycoon.core.money.Multiplier.of("0.03")
        assertEquals(s0.cash - l.price - fee, s1.cash)
        assertEquals(1, s1.estate.properties.size)
        val drop = engine.economy.netWorth(s0) - engine.economy.netWorth(s1)
        assertEquals(fee, drop, "net worth only loses the transaction cost")
    }

    @Test
    fun unlocksByChapterAndCity() {
        val s = newGame(4).withCash(100_000_000)
        val l = est.listings(s, harbour).first()
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.BuyProperty(l.id)))
        val solara = c.districts.first { it.city == "miami" }.id
        val s2 = atChapter(s, 2)
        assertInstanceOf(GameError.Locked::class.java, s2.reject(Command.BuyProperty(est.listings(s2, solara).first().id)))
        assertInstanceOf(GameError.Locked::class.java, s2.reject(Command.BuyProperty(est.listings(s2, harbour).first().id, ltv = 0.5)))
        atChapter(s, 3).run(Command.BuyProperty(est.listings(s2, harbour).first().id, ltv = 0.5))
    }

    @Test
    fun rentArrivesEverySixHoursOnceTenantMovesIn() {
        var (s, id) = buyCheapest(atChapter(newGame(5).withCash(10_000_000), 2))
        s = s.adv(13 * hour)
        val rent = -s.ledger.balance(Accounts.revenue("rent"))
        assertTrue(rent.isPositive, "rent paid")
        val expected = est.marketRent6h(s, s.estate.properties.getValue(id))
        // Two payouts (at most), each ~market rent × rent factor at 100 %.
        assertTrue(rent < expected * 3L, "rent $rent vs 6h market $expected")
        assertTrue(-s.ledger.balance(Accounts.expense("property_tax")) < Money.ZERO, "property tax charged")
        trial(s)
    }

    @Test
    fun rentFactorPeaksNearMarketRent() {
        val f = est.RENT_LEVELS.associateWith { est.rentFactor(it) }
        assertTrue(f.getValue(100) > f.getValue(80) && f.getValue(100) > f.getValue(120), "$f")
        assertTrue(est.vacancyHours(120) > est.vacancyHours(80))
    }

    @Test
    fun renovationTakesTimeAndRaisesValueAndRent() {
        var (s, id) = buyCheapest(atChapter(newGame(6).withHugeCash(), 2))
        val p0 = s.estate.properties.getValue(id)
        val v0 = est.propertyValue(s, p0)
        val r0 = est.marketRent6h(s, p0)
        s = s.run(Command.RenovateProperty(id))
        assertTrue(s.timers.any { it.kind == TimerKind.Renovation })
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.RenovateProperty(id)))
        assertEquals(Money.ZERO, est.expectedRent6h(s, s.estate.properties.getValue(id)), "no rent during works")
        s = s.adv(c.typeById.getValue(p0.type).renovationHours * hour + 1)
        val p1 = s.estate.properties.getValue(id)
        assertEquals(1, p1.renovations)
        assertEquals(1000, p1.condition)
        assertTrue(est.propertyValue(s, p1, p0.boughtAtSim) > v0)
        assertTrue(est.marketRent6h(s, p1, p0.boughtAtSim) > r0)
    }

    @Test
    fun saleCompletesAfterTimerWithGainBooked() {
        var (s, id) = buyCheapest(atChapter(newGame(7).withCash(100_000_000), 2))
        val before = s.cash
        s = s.run(Command.SellProperty(id))
        assertTrue(id in s.estate.properties)
        s = s.adv(7 * hour)
        assertTrue(id !in s.estate.properties)
        assertTrue(s.cash > before)
        assertTrue(s.ledger.balance(EstateEngine.PROPERTY).isZero, "cost basis released")
        trial(s)
    }

    @Test
    fun mortgageInterestIsChargedAndForeclosureNeverLeavesDebt() {
        var (s, id) = buyCheapest(atChapter(newGame(8).withCash(10_000_000), 3), ltv = 0.6)
        val loan = s.estate.properties.getValue(id).mortgage
        assertTrue(loan.isPositive)
        trial(s)
        // Leave no cash: interest is capitalised until the loan reaches 95 % of value, then the bank sells.
        val cashOut = s.cash
        s = s.copy(ledger = (Ledger.post(s.ledger, s.simTimeMs, com.arnavgames.tycoon.core.ledger.TxnCategory.Adjustment, "test drain",
            listOf(com.arnavgames.tycoon.core.ledger.Posting(Accounts.CASH, -cashOut), com.arnavgames.tycoon.core.ledger.Posting(Accounts.WORLD, cashOut))) as Ledger.Result.Ok).state)
        // Crash the district far below the loan.
        s = s.copy(estate = s.estate.copy(districtBoost = mapOf(harbour to kotlin.math.ln(0.3))))
        val r = engine.advance(s, 7 * hour)
        s = r.state
        assertTrue(id !in s.estate.properties, "foreclosed")
        assertTrue(r.events.any { it is GameEvent.Foreclosure })
        assertTrue(!s.cash.isNegative)
        assertTrue(s.ledger.balance(EstateEngine.MORTGAGES).isZero, "no debt left behind")
        trial(s)
    }

    @Test
    fun hostingABusinessCutsItsUpkeepAndStopsRent() {
        var s = atChapter(newGame(9).withHugeCash(), 2).run(Command.OpenBusiness("retail")).run(Command.BuyLevels("retail", 20)).run(Command.OpenBusiness("logistics"))
        val upkeep0 = engine.economy.businessRate(s, "retail").upkeep
        val district = c.districts.first { "retail_unit" in it.types && it.city == "lisbon" }.id
        var l = est.listings(s, district).firstOrNull { it.type == "retail_unit" }
        var t = s
        var guard = 0
        while (l == null && guard++ < 20) { t = t.adv(6 * hour); l = est.listings(t, district).firstOrNull { it.type == "retail_unit" } }
        s = t.run(Command.BuyProperty(l!!.id))
        val id = s.estate.nextPropertyId - 1
        s = s.run(Command.HostBusiness(id, "retail"))
        assertTrue(engine.economy.businessRate(s, "retail").upkeep < upkeep0)
        assertEquals(Money.ZERO, est.expectedRent6h(s, s.estate.properties.getValue(id)))
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.HostBusiness(id, "logistics")))
    }

    @Test
    fun tenantDecisionsQueueAndNeverResolveThemselves() {
        var s = atChapter(newGame(10).withHugeCash(), 2)
        repeat(6) { s = buyCheapest(s, c.districts.filter { it.city == "lisbon" }[it % 6].id).first }
        s = s.adv(10 * day)
        assertTrue(s.estate.events.size in 1..c.config.maxQueuedEvents, "events ${s.estate.events.size}")
        val ev = s.estate.events.first()
        val s2 = s.run(Command.ResolveTenantEvent(ev.id, 0))
        assertTrue(s2.estate.events.none { it.id == ev.id })
        trial(s2)
    }

    @Test
    fun skyscraperIsBuiltInStagesAndLiftsTheDistrict() {
        var s = atChapter(newGame(11).withHugeCash(), 5)
        val cbd = c.districts.first { it.cbd && it.city == "london" && "land" in it.types }.id
        var l = est.listings(s, cbd).firstOrNull { it.type == "land" }
        var guard = 0
        while (l == null && guard++ < 40) { s = s.adv(6 * hour); l = est.listings(s, cbd).firstOrNull { it.type == "land" } }
        s = s.run(Command.BuyProperty(l!!.id))
        val id = s.estate.nextPropertyId - 1
        val design = c.towers.first()
        repeat(4) {
            s = s.run(Command.BuildTower(id, design.id))
            s = s.adv(design.stageHours.max() * hour + 1)
        }
        val tw = s.estate.properties.getValue(id).tower!!
        assertEquals(4, tw.stagesDone)
        assertTrue((s.estate.districtBoost[cbd] ?: 0.0) > 0.0, "landmark boost")
        s = s.adv(day)
        assertTrue(est.marketRent6h(s, s.estate.properties.getValue(id)) > Money.ZERO)
        assertInstanceOf(GameError.Invalid::class.java, atChapter(newGame(12).withHugeCash(), 5).let { g ->
            val (g2, pid) = buyCheapest(g)
            g2.reject(Command.BuildTower(pid, design.id))
        })
    }

    @Test
    fun insuredPropertiesShrugOffDisasters() {
        var s = atChapter(newGame(13).withHugeCash(), 2)
        repeat(8) { i -> s = buyCheapest(s, c.districts.filter { it.city == "lisbon" }[i % 6].id, insured = true).first }
        val r = engine.advance(s, 30 * day)
        val disasters = r.events.filterIsInstance<GameEvent.Disaster>()
        assertTrue(disasters.isNotEmpty(), "some disasters in 30 days")
        assertTrue(disasters.all { it.insured })
        assertTrue(r.state.estate.properties.values.none { it.condition < 200 })
    }

    /** Estates follow the same rule as everything else: one long advance equals many short ones. */
    @Test
    fun offlineEqualsLiveWithProperties() {
        var s = atChapter(newGame(14).withHugeCash(), 3)
        repeat(4) { i -> s = buyCheapest(s, c.districts.filter { it.city == "london" }[i].id, ltv = 0.5).first }
        s = s.run(Command.RenovateProperty(s.estate.properties.keys.first()))
        val a = s.adv(2 * day)
        var b = s
        repeat(48) { b = b.adv(hour) }
        assertEquals(a.estate, b.estate)
        assertEquals(a.ledger.balances, b.ledger.balances)
    }

    @Test
    fun missionCountsProperties() {
        var s = atChapter(newGame(15).withCash(10_000_000), 2)
        assertEquals(0.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.PropertiesOwned(1)))
        s = buyCheapest(s).first
        assertEquals(1.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.PropertiesOwned(1)))
    }
}
