package com.arnavgames.tycoon.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfflineCalculatorTest {
    private val hour = 3_600_000L
    private val cap = 2 * hour

    @Test
    fun honestAbsenceSameBootIsCreditedUpToCap() {
        val clock = FakeClock()
        val saved = OfflineCalculator.initial(clock)
        clock.advance(hour)
        val r = OfflineCalculator.compute(saved, clock, cap)
        assertEquals(hour, r.creditMs)
        assertEquals(TimeFlag.None, r.flag)
        clock.advance(5 * hour)
        val r2 = OfflineCalculator.compute(r.newClock, clock, cap)
        assertEquals(cap, r2.creditMs)
        assertEquals(TimeFlag.Capped, r2.flag)
    }

    @Test
    fun forwardClockJumpOnSameBootOnlyCreditsRealTime() {
        val clock = FakeClock()
        val saved = OfflineCalculator.initial(clock)
        clock.advance(10 * 60_000L)              // 10 real minutes
        clock.changeWallClock(clock.wall + 24 * hour)    // user jumps the clock a day ahead
        val r = OfflineCalculator.compute(saved, clock, cap)
        assertEquals(10 * 60_000L, r.creditMs)
        assertEquals(TimeFlag.ForwardJumpDetected, r.flag)
        assertEquals(1, r.newClock.suspicionCount)
    }

    @Test
    fun clockTricksNeverBlockRealTime() {
        val clock = FakeClock()
        var saved = OfflineCalculator.initial(clock)
        clock.reboot("b2")
        clock.changeWallClock(clock.wall + 10 * hour)    // after a reboot we cannot verify: credit is capped
        var r = OfflineCalculator.compute(saved, clock, cap)
        assertEquals(cap, r.creditMs)
        saved = r.newClock
        clock.changeWallClock(clock.wall - 10 * hour)    // set the clock back to the truth
        r = OfflineCalculator.compute(saved, clock, cap)
        assertEquals(0, r.creditMs, "no real time passed")
        assertEquals(TimeFlag.ClockWentBackwards, r.flag)
        saved = r.newClock
        clock.advance(hour)                       // real time passes: it counts, the old mark no longer blocks it
        r = OfflineCalculator.compute(saved, clock, cap)
        assertEquals(hour, r.creditMs)
    }

    @Test
    fun aMarkStuckInTheFutureDoesNotBlockOfflineTime() {
        // The 1.2 save that earned nothing offline: the mark was 3.4 days ahead of the real clock.
        val clock = FakeClock()
        val now = clock.wall
        val stuck = ClockState(lastWall = now, lastMono = 0, bootId = "boot-1", maxCreditedWall = now + 82 * hour, suspicionCount = 2)
        clock.advance(hour)
        assertEquals(hour, OfflineCalculator.compute(stuck, clock, 6 * hour).creditMs, "same boot: real elapsed time")
        clock.reboot("b9")
        clock.changeWallClock(now + 5 * hour)
        val r = OfflineCalculator.compute(stuck, clock, 6 * hour)
        assertEquals(5 * hour, r.creditMs, "after a reboot: time since the last session")
        assertTrue(r.newClock.maxCreditedWall <= clock.wall, "the stuck mark is repaired")
    }

    @Test
    fun aForwardJumpDuringPlayCannotPushTheMarkAhead() {
        val clock = FakeClock()
        var saved = OfflineCalculator.initial(clock)
        val start = clock.wall
        clock.advance(60_000L)
        clock.changeWallClock(clock.wall + 72 * hour)
        saved = OfflineCalculator.touch(saved, clock)
        clock.advance(60_000L)
        saved = OfflineCalculator.touch(saved, clock)
        assertTrue(saved.maxCreditedWall <= start + 2 * 60_000L + 2 * OfflineCalculator.TOLERANCE_MS, "mark only follows real time")
    }

    @Test
    fun serverTimeOverridesWrongDeviceClock() {
        val clock = FakeClock()
        val saved = OfflineCalculator.initial(clock)
        clock.reboot("b2")
        val trueNow = clock.wall + 30 * 60_000L
        clock.changeWallClock(clock.wall + 50 * hour)
        val r = OfflineCalculator.compute(saved, clock, cap, serverWallMs = trueNow)
        assertEquals(30 * 60_000L, r.creditMs)
        assertEquals(TimeFlag.ServerMismatch, r.flag)
    }

    @Test
    fun creditIsNeverNegativeOrAboveCap() {
        val clock = FakeClock()
        var saved = OfflineCalculator.initial(clock)
        val rng = com.arnavgames.tycoon.core.random.GameRandom.stream(7, "clock-fuzz")
        repeat(10_000) {
            when (rng.nextInt(4)) {
                0 -> clock.advance(rng.nextLong().mod(20 * hour))
                1 -> clock.changeWallClock(clock.wall + (rng.nextLong() % (30 * hour)))
                2 -> clock.reboot("b${rng.nextInt(1000)}")
                else -> clock.mono += rng.nextLong().mod(hour)
            }
            val sameBoot = clock.bootId() == saved.bootId
            val mono = clock.monotonicMillis() - saved.lastMono
            val r = OfflineCalculator.compute(saved, clock, cap)
            assertTrue(r.creditMs in 0..cap)
            if (sameBoot) assertTrue(r.creditMs <= maxOf(0L, mono), "same boot never credits more than real time")
            assertTrue(r.newClock.maxCreditedWall <= clock.wall, "the mark is never ahead of the clock")
            saved = r.newClock
        }
    }
}
