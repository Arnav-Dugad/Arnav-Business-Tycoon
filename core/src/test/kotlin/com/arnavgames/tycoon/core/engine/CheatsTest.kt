package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheatsTest {
    @Test
    fun aCleanSaveIsNotASandbox() {
        assertFalse(Cheats.isSandbox(newGame()))
        assertFalse(Cheats.isSandbox(busyState()))
    }

    @Test
    fun everyCheatWorksAndMarksTheSaveForGood() {
        val start = busyState()
        Cheats.KINDS.forEach { kind ->
            val after = start.run(Command.Cheat(kind))
            assertEquals(1, Cheats.uses(after), kind)
            assertTrue(Cheats.isSandbox(after), kind)
            // The ledger still balances after every cheat (double entry).
            assertTrue(com.arnavgames.tycoon.core.ledger.Ledger.trialBalance(after.ledger).isZero, kind)
        }
    }

    @Test
    fun cashCheatsAddWhatTheySay() {
        val s = busyState()
        val hour = s.run(Command.Cheat(Cheats.CASH_HOUR))
        assertEquals(s.cash + maxOf(Cheats.incomeOver(EngineFixtures.engine, s, 3_600), Money.ofDollars(1_000)), hour.cash)
        val doubled = s.run(Command.Cheat(Cheats.CASH_DOUBLE))
        assertEquals(s.cash + s.cash, doubled.cash)
    }

    @Test
    fun skipAdvancesTimeAndFinishCompletesEveryTimer() {
        val s = busyState()
        assertTrue(s.timers.isNotEmpty())
        val skipped = s.run(Command.Cheat(Cheats.SKIP_HOUR))
        assertEquals(s.simTimeMs + 3_600_000L, skipped.simTimeMs)
        val done = s.run(Command.Cheat(Cheats.FINISH_TIMERS))
        assertTrue(done.timers.isEmpty())
        assertTrue("market_stall" in done.businesses.getValue("retail").expansionsDone)
    }

    @Test
    fun levelsCheatRaisesEveryBusinessByTen() {
        val s = busyState()
        val after = s.run(Command.Cheat(Cheats.LEVELS))
        s.businesses.forEach { (id, b) -> assertEquals(b.level + 10, after.businesses.getValue(id).level) }
    }

    @Test
    fun unknownCheatsAreRejected() {
        newGame().reject(Command.Cheat("infinite_money"))
    }

    @Test
    fun theSandboxMarkSurvivesFurtherPlay() {
        val s = busyState().run(Command.Cheat(Cheats.BULLION)).run(Command.Cheat(Cheats.FAVOR))
        assertEquals(2, Cheats.uses(s))
        assertTrue(Cheats.isSandbox(EngineFixtures.engine.advance(s, 60_000).state))
    }
}
