package com.arnavgames.tycoon.sim

import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CI smoke batch (docs/TEST_PLAN.md §1): every bot runs a few seeds without errors, runs are reproducible,
 * and early pacing stays in a loose band around the targets. The full batch lives in docs/balance/.
 */
class SimulationSmokeTest {
    private val loaded = ContentLoader.loadFromRepo()
    private val engine = GameEngine(loaded.pack)
    private val sim = Simulation(engine, loaded.hash)

    @Test
    fun everyBotRunsWithoutErrors() {
        for (name in listOf("active", "idle", "cautious", "poor_choices")) {
            for (seed in 1L..3L) {
                val r = sim.run(botByName(name), seed, days = 1)
                assertNull(r.error, "$name seed $seed: ${r.error}")
                assertTrue(r.finalNetWorth.isPositive, "$name made money")
            }
        }
    }

    @Test
    fun runsAreReproducible() {
        val a = sim.run(ActiveBot(), 42, days = 1)
        val b = sim.run(ActiveBot(), 42, days = 1)
        assertEquals(a.milestones, b.milestones)
        assertEquals(a.finalNetWorth, b.finalNetWorth)
    }

    @Test
    fun earlyPacingStaysNearTargets() {
        val r = sim.run(ActiveBot(), 7, days = 1)
        val manager = r.milestones.getValue(Milestones.FIRST_MANAGER)
        val ch1 = r.milestones.getValue(Milestones.CHAPTER1_DONE)
        // Loose bands (×2 around the ECONOMY.md targets) so CI catches regressions, not noise.
        assertTrue(manager in 2.5..16.0, "first manager at $manager min")
        assertTrue(ch1 in 10.0..70.0, "chapter 1 at $ch1 min")
    }
}
