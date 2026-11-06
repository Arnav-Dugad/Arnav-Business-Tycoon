package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withCash
import com.arnavgames.tycoon.core.engine.EngineFixtures.withFavor
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.MechanicState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineTest {
    private val hour = 3_600_000L

    // ------------------------------------------------------------------ time

    @Test
    fun advanceIsSplitInvariant() {
        val start = busyState()
        val total = 3 * hour + 1234
        val whole = start.adv(total)
        repeat(25) { trial ->
            val rng = GameRandom.stream(trial.toLong(), "split")
            var s = start
            var left = total
            while (left > 0) {
                val step = minOf(left, 1 + rng.nextLong().mod(20 * 60_000L))
                s = s.adv(step)
                left -= step
            }
            assertEquals(whole, s, "trial $trial: split advances must produce identical state")
        }
    }

    @Test
    fun offlineCatchUpEqualsLivePlayToTheCent() {
        val start = busyState()
        var live = start
        repeat(8 * 3600) { live = live.adv(1000) } // 8 hours of 1-second live ticks
        val offline = start.adv(8 * hour)          // one offline catch-up
        assertEquals(offline, live)
        assertEquals(offline.cash, live.cash)
        assertTrue(offline.cash > start.cash, "managed businesses earned money")
    }

    @Test
    fun unmanagedBusinessStopsAtStorageCap() {
        var s = newGame().run(Command.OpenBusiness("retail"))
        s = s.adv(10 * 60_000L)
        val b = s.businesses.getValue("retail")
        val cap = engine.economy.storageCap(s, "retail")
        assertEquals(cap, b.stored)
        assertEquals(Money.ZERO, s.cash, "nothing reaches cash without collecting")
        s = s.run(Command.Collect("retail"))
        assertEquals(cap, s.cash)
        assertEquals(Money.ZERO, Ledger.trialBalance(s.ledger))
    }

    @Test
    fun expansionsCompleteOnTimeAndRaiseIncome() {
        var s = newGame().withCash(10_000).run(Command.OpenBusiness("retail")).run(Command.BuyLevels("retail", 10))
        val before = engine.economy.businessRate(s, "retail").net
        s = s.run(Command.StartExpansion("retail", "market_stall"))
        assertEquals(1, s.timers.size)
        s = s.adv(59_999)
        assertTrue("market_stall" !in s.businesses.getValue("retail").expansionsDone)
        val r = engine.advance(s, 1)
        s = r.state
        assertTrue("market_stall" in s.businesses.getValue("retail").expansionsDone)
        assertTrue(r.events.any { it is GameEvent.ExpansionCompleted })
        assertTrue(engine.economy.businessRate(s, "retail").net > before)
    }

    // ------------------------------------------------------------------ commands

    @Test
    fun buyingLevelsCostsExactlyTheFormula() {
        var s = newGame().withCash(1_000).run(Command.OpenBusiness("retail"))
        val def = engine.content.business("retail")
        val cost = engine.economy.levelCost(def, 1, 5)
        val cash = s.cash
        s = s.run(Command.BuyLevels("retail", 5))
        assertEquals(cash - cost, s.cash)
        assertEquals(6, s.businesses.getValue("retail").level)
        // cost of 5 levels equals sum of single-level costs (within rounding of one cent per level)
        val singles = (1..5).fold(Money.ZERO) { a, l -> a + engine.economy.levelCost(def, l, 1) }
        assertTrue((singles - cost).abs() <= Money.ofCents(5))
    }

    @Test
    fun buyMaxBuysTheMostAffordable() {
        var s = newGame().withCash(500).run(Command.OpenBusiness("retail"))
        s = s.run(Command.BuyLevels("retail", -1))
        val def = engine.content.business("retail")
        val lvl = s.businesses.getValue("retail").level
        assertTrue(s.cash < engine.economy.levelCost(def, lvl, 1), "could not afford one more")
        assertTrue(!s.cash.isNegative)
    }

    @Test
    fun typedErrorsForInvalidCommands() {
        val s = newGame()
        assertInstanceOf(GameError.NotFound::class.java, s.reject(Command.BuyLevels("retail", 1)))
        val opened = s.run(Command.OpenBusiness("retail"))
        assertInstanceOf(GameError.InsufficientFunds::class.java, opened.reject(Command.BuyLevels("retail", 1)))
        assertInstanceOf(GameError.InsufficientFunds::class.java, opened.reject(Command.OpenBusiness("restaurants")))
        assertInstanceOf(GameError.AlreadyDone::class.java, opened.reject(Command.OpenBusiness("retail")))
        assertInstanceOf(GameError.Invalid::class.java, opened.reject(Command.SetRetailPrice("retail", 999)))
    }

    @Test
    fun slotLimitIsEnforced() {
        var s = newGame().withCash(1_000_000)
        s = s.run(Command.OpenBusiness("retail")).run(Command.OpenBusiness("restaurants")).run(Command.OpenBusiness("logistics"))
        assertEquals(3, engine.economy.usedSlots(s))
        // Mothballing frees a slot; re-activating needs one.
        s = s.run(Command.SetMothballed("logistics", true))
        assertEquals(2, engine.economy.usedSlots(s))
        s = s.run(Command.SetMothballed("logistics", false))
        assertEquals(3, engine.economy.usedSlots(s))
    }

    @Test
    fun hiringAManagerCollectsAndAutomates() {
        var s = newGame().withCash(1_000).run(Command.OpenBusiness("retail")).adv(20_000)
        val stored = s.businesses.getValue("retail").stored
        assertTrue(stored.isPositive)
        val cashBefore = s.cash
        s = s.run(Command.HireManager("retail", "retail_mira"))
        val mgr = engine.content.business("retail").managers.first { it.id == "retail_mira" }
        assertEquals(cashBefore - mgr.cost + stored, s.cash)
        val c = s.cash
        s = s.adv(60_000)
        assertTrue(s.cash > c, "managed business pays straight into cash")
        assertEquals(Money.ZERO, s.businesses.getValue("retail").stored)
        // Manager sets the price; manual pricing is locked.
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.SetRetailPrice("retail", 120)))
    }

    @Test
    fun hustleBuildsComboAndPays() {
        var s = newGame()
        s = s.run(Command.HustleTap(1))
        val first = s.cash
        s = s.adv(300).run(Command.HustleTap(1))
        assertTrue(s.cash - first > first, "second tap in the combo window pays more")
        assertEquals(2, s.hustle.combo)
        s = s.adv(5_000).run(Command.HustleTap(1))
        assertEquals(1, s.hustle.combo, "combo resets after the window")
        assertEquals(3, s.hustle.totalTaps)
    }

    @Test
    fun frenzyHasDailyCharges() {
        var s = newGame().run(Command.OpenBusiness("retail"))
        repeat(3) {
            s = s.run(Command.ActivateFrenzy)
            assertInstanceOf(GameError.AlreadyDone::class.java, s.reject(Command.ActivateFrenzy))
            s = s.adv(31_000)
        }
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ActivateFrenzy))
        s = s.adv(GameEngine.DAY)
        s = s.run(Command.ActivateFrenzy) // new day, charges reset
    }

    @Test
    fun rushTimerSpendsFavor() {
        var s = newGame().withCash(10_000).withFavor(3).run(Command.OpenBusiness("retail")).run(Command.BuyLevels("retail", 10))
        s = s.run(Command.StartExpansion("retail", "market_stall"))
        val t = s.timers.single()
        assertEquals(1, engine.rushCostFavor(s, t))
        s = s.run(Command.RushTimer(t.id))
        assertTrue(s.timers.isEmpty())
        assertTrue("market_stall" in s.businesses.getValue("retail").expansionsDone)
        assertEquals(Money.ofCents(2), s.ledger.favor)
    }

    // ------------------------------------------------------------------ mechanics

    @Test
    fun retailOptimalPriceBeatsDefaultAndExtremesHurt() {
        val m = engine.content.business("retail").mechanic as MechanicDef.Retail
        val opt = Mechanics.retailOptimalPricePct(m)
        val base = Mechanics.retailProfitFactor(m, 100)
        assertEquals(1.0, base, 1e-9)
        assertTrue(Mechanics.retailProfitFactor(m, opt) > 1.05)
        assertTrue(Mechanics.retailProfitFactor(m, m.minPricePct) < 0.9)
    }

    @Test
    fun restaurantReputationDriftsTowardTheChosenTier() {
        var s = newGame().withCash(10_000).run(Command.OpenBusiness("restaurants"))
        s = s.run(Command.SetRestaurantQuality("restaurants", "fine"))
        val start = (s.businesses.getValue("restaurants").mechanic as MechanicState.Restaurant).milliStars
        s = s.adv(2 * hour)
        val after = (s.businesses.getValue("restaurants").mechanic as MechanicState.Restaurant).milliStars
        assertTrue(after > start + 900, "about +1 star in 2 hours at 0.5/h (was $start → $after)")
        s = s.adv(10 * hour)
        assertEquals(4700, (s.businesses.getValue("restaurants").mechanic as MechanicState.Restaurant).milliStars)
    }

    @Test
    fun logisticsPlanningBeatsTheDefaultDepotAssignment() {
        var s = newGame().withCash(10_000_000).run(Command.OpenBusiness("logistics")).run(Command.BuyLevels("logistics", 39))
        val default = engine.economy.businessRate(s, "logistics").efficiency
        s = s.run(Command.AutoPlanRoutes("logistics"))
        val planned = engine.economy.businessRate(s, "logistics").efficiency
        assertTrue(planned > default * 1.5, "default $default vs planned $planned")
        val m = engine.content.business("logistics").mechanic as MechanicDef.Logistics
        val optimal = Mechanics.logisticsEfficiency(m, 40, MechanicState.Logistics(Mechanics.logisticsPlan(m, 40, 1.0)))
        assertTrue(optimal >= planned, "a perfect plan is at least as good as auto-plan")
    }

    // ------------------------------------------------------------------ progression & invariants

    @Test
    fun missionsCompleteAndChapterAdvances() {
        var s = newGame().withCash(1_000_000).withFavor(100)
        s = s.run(Command.OpenBusiness("retail"))
        assertTrue("c1_open_kiosk" in s.progress.claimableMissions)
        repeat(60) { s = s.run(Command.HustleTap(1)) }
        s = s.run(Command.BuyLevels("retail", 9)).run(Command.HireManager("retail", "retail_mira"))
            .run(Command.OpenBusiness("restaurants"))
            .run(Command.StartExpansion("retail", "market_stall"))
        s = s.run(Command.RushTimer(s.timers.single().id))
        s = s.run(Command.BuyLevels("restaurants", 40)).run(Command.HireManager("restaurants", "rest_amara"))
            .run(Command.BuyLevels("retail", 60))
        s = s.adv(60_000)
        val chapter1 = engine.content.chapters.first().missions.map { it.id }
        assertTrue(s.progress.completedMissions.containsAll(chapter1), "all chapter 1 missions done: ${s.progress.completedMissions}")
        chapter1.forEach { s = s.run(Command.ClaimMission(it)) }
        assertEquals(2, s.progress.chapter)
        assertTrue(s.ledger.bullion.isPositive)
    }

    @Test
    fun ledgerStaysBalancedThroughAGameDay() {
        var s = busyState()
        repeat(24) {
            s = s.adv(hour).run(Command.CollectAll)
            val def = engine.content.business("restaurants")
            if (engine.economy.maxAffordable(def, s.businesses.getValue("restaurants").level, s.cash) > 0) {
                s = s.run(Command.BuyLevels("restaurants", -1))
            }
        }
        assertEquals(Money.ZERO, Ledger.trialBalance(s.ledger))
        assertTrue(s.stats.peakNetWorth >= engine.economy.netWorth(s) || s.stats.peakNetWorth.isPositive)
    }

    @Test
    fun deterministicForSameSeedAndCommands() {
        fun play(): com.arnavgames.tycoon.core.state.GameState {
            var s = busyState()
            repeat(50) { i -> s = s.adv(37_000L * (i + 1)).run(Command.CollectAll) }
            return s
        }
        assertEquals(play(), play())
    }
}
