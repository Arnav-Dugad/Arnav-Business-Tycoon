package com.arnavgames.tycoon.core.progression

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressionTest {
    private val day = 86_400_000L
    private val p = engine.content.progression
    private val dyn = engine.dynasty

    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")

    /** A founder who has finished chapter 8 with a $4×10^21 peak fortune and owns a few luxury pieces. */
    private fun founderReady(): GameState {
        var s = busyState().withHugeCash()
        val ch8 = engine.content.chapters.maxBy { it.number }
        s = s.copy(progress = s.progress.copy(chapter = 8, completedMissions = s.progress.completedMissions + ch8.missions.map { it.id }))
        engine.content.luxury.items.filter { it.category == "watch" }.take(3).forEach { s = s.run(Command.BuyLuxury(it.id)) }
        // Five game days into the generation, so rival fortunes have grown.
        return s.copy(simTimeMs = s.simTimeMs + 5 * day, stats = s.stats.copy(peakNetWorth = Money.ofDollars(java.math.BigDecimal("4e21"))))
    }

    @Test
    fun dailyMissionsAreAssignedProgressAndPay() {
        var s = busyState().adv(60_000)
        assertEquals(p.config.dailyCount, s.meta.daily.size)
        assertEquals(p.config.dailyCount, s.meta.daily.map { it.metric }.toSet().size, "distinct metrics")
        // Make a "levels" mission and complete it.
        val idx = s.meta.daily.indexOfFirst { it.metric == "levels" }.takeIf { it >= 0 }
        if (idx != null) {
            val m = s.meta.daily[idx]
            s = s.withHugeCash().run(Command.BuyLevels("retail", m.target.toInt()))
            assertTrue(engine.progression.done(s, s.meta.daily[idx]))
            val bullion = s.ledger.bullion
            s = s.run(Command.ClaimDaily(idx))
            assertTrue(s.meta.daily[idx].claimed)
            assertEquals(bullion + Money.ofCents(m.bullion), s.ledger.bullion)
            assertTrue(s.meta.seasonXp >= m.seasonXp)
            assertInstanceOf(GameError.AlreadyDone::class.java, s.reject(Command.ClaimDaily(idx)))
        }
        // A new game day brings new dailies.
        val next = s.adv(day)
        assertEquals(dyn.day(next.simTimeMs), next.meta.dailyDay)
        trial(next)
    }

    @Test
    fun countersFollowEvents() {
        var s = busyState()
        val taps = engine.progression.counter(s, "taps")
        s = s.run(Command.HustleTap(7))
        assertEquals(taps + 7, engine.progression.counter(s, "taps"))
        val levels = engine.progression.counter(s, "levels")
        s = s.withHugeCash().run(Command.BuyLevels("retail", 3))
        assertEquals(levels + 3, engine.progression.counter(s, "levels"))
    }

    @Test
    fun rerollIsFreeOnceADay() {
        val s = busyState().adv(60_000)
        val r = s.run(Command.RerollDaily(0))
        assertTrue(r.meta.daily[0].template != s.meta.daily[0].template)
        assertInstanceOf(GameError.Locked::class.java, r.reject(Command.RerollDaily(1)))
    }

    @Test
    fun theDailyLadderPausesButNeverResets() {
        var s = busyState()
        s = s.run(Command.ClaimDailyReward)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ClaimDailyReward))
        s = s.adv(3 * day) // three missed days
        s = s.run(Command.ClaimDailyReward)
        assertEquals(2, s.meta.ladderClaimed, "the second step, not back to the first")
        trial(s)
    }

    @Test
    fun achievementsUnlockAndPayBullion() {
        var s = busyState().adv(60_000)
        assertTrue("open_retail" in s.meta.achievementsClaimable)
        val bullion = s.ledger.bullion
        s = s.run(Command.ClaimAchievement("open_retail"))
        assertTrue("open_retail" in s.meta.achievements)
        assertEquals(bullion + Money.ofCents(p.achievementById.getValue("open_retail").bullion), s.ledger.bullion)
        assertInstanceOf(GameError.AlreadyDone::class.java, s.reject(Command.ClaimAchievement("open_retail")))
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ClaimAchievement("gen_10")))
    }

    @Test
    fun seasonTiersNeedXpAndPremiumIsLocked() {
        var s = busyState().adv(60_000)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ClaimSeasonTier(1)))
        s = s.copy(meta = s.meta.copy(seasonXp = 250))
        s = s.run(Command.ClaimSeasonTier(1)).run(Command.ClaimSeasonTier(2))
        assertEquals(setOf(1, 2), s.meta.seasonClaimedFree)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ClaimSeasonTier(3)))
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.ClaimSeasonTier(1, premium = true)))
    }

    @Test
    fun legacyFollowsTheFormula() {
        val g = dyn.baseLegacyGain(Money.ofDollars(java.math.BigDecimal("4e21")))
        assertTrue(g in 120..140, "first prestige at the chapter 8 goal gives ~130 Legacy, got $g")
        assertTrue(dyn.legacyMultiplier(g) in 2.4..2.8)
        assertEquals(1.0, dyn.legacyMultiplier(0))
    }

    @Test
    fun passingTheCrownStartsAStrongerHeir() {
        val s = founderReady()
        val pv = engine.crownPreview(s)
        assertTrue(pv.legacyGain > 0 && pv.traits.size == 3)
        val heirloom = s.luxury.owned.keys.first()
        val charter = pv.charter.filter { it.endsWith("_1_a") }.toSet()
        val heir = s.run(Command.PassTheCrown(pv.traits.first(), listOf(heirloom), "self_made", charter))
        assertEquals(2, heir.dynasty.generation)
        assertEquals(pv.newLegacy, heir.dynasty.legacy)
        assertEquals(charter, heir.dynasty.charter)
        assertTrue(heir.businesses.isEmpty() && heir.progress.chapter == 1)
        assertEquals(setOf(heirloom), heir.luxury.owned.keys)
        assertEquals(s.ledger.bullion, heir.ledger.bullion, "Bullion carries over")
        assertEquals(s.simTimeMs, heir.dynasty.generationStartSim)
        // Rivals restart with the generation.
        val r = engine.content.world.rivals.first()
        assertTrue(engine.world.rivalLog10(heir, r) < engine.world.rivalLog10(s, r))
        // The challenge is enforced.
        assertInstanceOf(GameError.Locked::class.java, heir.reject(Command.TakeLoan(Money.ofDollars(1), 1)))
        // The Legacy multiplier lifts income.
        val h1 = heir.run(Command.OpenBusiness("retail"))
        assertTrue(engine.dynasty.incomeFactor(h1, "retail") > 2.0)
        trial(heir)
    }

    @Test
    fun crownRulesAreChecked() {
        val early = busyState()
        assertInstanceOf(GameError.Locked::class.java, early.reject(Command.PassTheCrown("frugal", emptyList(), null, emptySet())))
        val s = founderReady()
        val pv = engine.crownPreview(s)
        val notOffered = p.traits.map { it.id }.first { it !in pv.traits }
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.PassTheCrown(notOffered, emptyList(), null, emptySet())))
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.PassTheCrown(pv.traits.first(), s.luxury.owned.keys.toList(), null, emptySet())), "too many heirlooms")
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.PassTheCrown(pv.traits.first(), emptyList(), null, setOf("industry_1_a", "industry_1_b"))), "one per tier")
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.PassTheCrown(pv.traits.first(), emptyList(), null, setOf("industry_10_a"))), "needs more Legacy")
    }

    @Test
    fun theIpoRaisesCashForAShareOfIncome() {
        var s = busyState().withHugeCash()
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.LaunchIpo(0.2)))
        s = s.copy(holdingName = "Aurum Holdings", progress = s.progress.copy(chapter = 7))
        val before = engine.economy.businessRate(s, "retail").net
        val cash = s.cash
        val r = s.run(Command.LaunchIpo(0.2))
        assertNotNull(r.dynasty.ipo)
        assertTrue(r.cash > cash)
        assertTrue(engine.economy.businessRate(r, "retail").net < before)
        assertInstanceOf(GameError.AlreadyDone::class.java, r.reject(Command.LaunchIpo(0.1)))
        trial(r)
    }

    @Test
    fun weeklyEventsAndBonusesApply() {
        val s = busyState().adv(60_000)
        assertNotNull(dyn.weeklyEvent(s))
        assertTrue(s.meta.weekly.size == p.config.weeklyCount)
        assertFalse(s.meta.eventGoals.isEmpty())
    }

    @Test
    fun progressionIsIdenticalLiveAndOffline() {
        val base = busyState()
        val a = base.adv(30 * 3_600_000L)
        var b = base
        repeat(30) { b = b.adv(3_600_000L) }
        assertEquals(a.meta, b.meta)
        assertEquals(a.dynasty, b.dynasty)
        assertEquals(a.ledger.balances, b.ledger.balances)
    }
}
