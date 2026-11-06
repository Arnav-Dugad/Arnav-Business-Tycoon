package com.arnavgames.tycoon.core.time

import kotlinx.serialization.Serializable

/** Clock abstraction (docs/ARCHITECTURE.md §3.2). The app supplies System/SystemClock; tests and the simulator use [FakeClock]. */
interface GameClock {
    /** Wall-clock epoch millis (user-changeable). */
    fun wallMillis(): Long
    /** Monotonic millis since boot (cannot be changed by the user; resets on reboot). */
    fun monotonicMillis(): Long
    /** Identifier of the current boot, so monotonic values from different boots are never compared. */
    fun bootId(): String
}

class FakeClock(var wall: Long = 1_800_000_000_000L, var mono: Long = 0L, var boot: String = "boot-1") : GameClock {
    override fun wallMillis() = wall
    override fun monotonicMillis() = mono
    override fun bootId() = boot

    /** Real time passes: both clocks move. */
    fun advance(ms: Long) { wall += ms; mono += ms }
    /** The user changes the wall clock only. */
    fun changeWallClock(newWall: Long) { wall = newWall }
    fun reboot(newBoot: String) { boot = newBoot; mono = 0 }
}

/** What the save remembers about time, used to compute offline credit safely. */
@Serializable
data class ClockState(
    val lastWall: Long,
    val lastMono: Long,
    val bootId: String,
    /** Highest wall time ever credited. Offline time only counts after this mark. */
    val maxCreditedWall: Long,
    /** Number of times tampering was suspected (used only to tighten leaderboard plausibility). */
    val suspicionCount: Int = 0,
)

enum class TimeFlag { None, ClockWentBackwards, ForwardJumpDetected, ServerMismatch, Capped }

data class OfflineCredit(val creditMs: Long, val flag: TimeFlag, val newClock: ClockState, val rawWallMs: Long)

/**
 * Computes how much offline time to credit on resume. Rules (docs/ARCHITECTURE.md §6):
 * 1. Same boot: wall delta may not exceed the monotonic delta by more than [TOLERANCE_MS]; otherwise only the monotonic delta counts.
 * 2. High-water mark: time is only credited after [ClockState.maxCreditedWall] (setting the clock back and forward again gives nothing).
 * 3. Never negative. 4. Optional server time overrides the device clock when they disagree by more than 10 minutes.
 * 5. Credit is capped at [capMs].
 */
object OfflineCalculator {
    const val TOLERANCE_MS = 2 * 60_000L
    const val SERVER_TOLERANCE_MS = 10 * 60_000L

    fun compute(saved: ClockState, clock: GameClock, capMs: Long, serverWallMs: Long? = null): OfflineCredit {
        require(capMs >= 0)
        val deviceWall = clock.wallMillis()
        var flag = TimeFlag.None
        var wallNow = deviceWall
        if (serverWallMs != null && kotlin.math.abs(serverWallMs - deviceWall) > SERVER_TOLERANCE_MS) {
            wallNow = serverWallMs
            flag = TimeFlag.ServerMismatch
        }
        val start = maxOf(saved.lastWall, saved.maxCreditedWall)
        var credit: Long
        if (wallNow <= start) {
            credit = 0
            if (wallNow < saved.lastWall) flag = TimeFlag.ClockWentBackwards
        } else {
            credit = wallNow - start
            if (clock.bootId() == saved.bootId) {
                val monoDelta = (clock.monotonicMillis() - saved.lastMono).coerceAtLeast(0)
                if (credit > monoDelta + TOLERANCE_MS) {
                    credit = monoDelta
                    flag = TimeFlag.ForwardJumpDetected
                }
            }
        }
        val raw = credit
        if (credit > capMs) {
            credit = capMs
            if (flag == TimeFlag.None) flag = TimeFlag.Capped
        }
        val suspicious = flag == TimeFlag.ForwardJumpDetected || flag == TimeFlag.ClockWentBackwards || flag == TimeFlag.ServerMismatch
        val newClock = ClockState(
            lastWall = wallNow,
            lastMono = clock.monotonicMillis(),
            bootId = clock.bootId(),
            maxCreditedWall = maxOf(saved.maxCreditedWall, wallNow),
            suspicionCount = saved.suspicionCount + if (suspicious) 1 else 0,
        )
        return OfflineCredit(credit, flag, newClock, raw)
    }

    /** Called on every save during live play: records "now" without crediting anything. */
    fun touch(saved: ClockState, clock: GameClock): ClockState {
        val wall = clock.wallMillis()
        return saved.copy(
            lastWall = wall,
            lastMono = clock.monotonicMillis(),
            bootId = clock.bootId(),
            maxCreditedWall = maxOf(saved.maxCreditedWall, wall),
        )
    }

    fun initial(clock: GameClock) = ClockState(clock.wallMillis(), clock.monotonicMillis(), clock.bootId(), clock.wallMillis())
}
