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
 * 1. Same boot: the monotonic clock (which the user can't change) is the truth. Real elapsed time is credited, whatever
 *    the wall clock says; changing the date neither adds nor removes time.
 * 2. After a reboot the monotonic clock restarts, so the wall clock is used, from the later of the last session and the
 *    high-water mark. A mark that is ahead of the device clock came from a bad clock reading (or a clock set back since)
 *    and is ignored: one wrong reading must never block real time for days (this happened to a real save in 1.2).
 * 3. The high-water mark only advances by verified time, so a forward jump can't push it into the future.
 * 4. Never negative. 5. Optional server time overrides the device clock when they disagree by more than 10 minutes.
 * 6. Credit is capped at [capMs].
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
        val sameBoot = clock.bootId() == saved.bootId && flag != TimeFlag.ServerMismatch
        var credit: Long
        val verifiedNow: Long
        if (sameBoot) {
            val monoDelta = (clock.monotonicMillis() - saved.lastMono).coerceAtLeast(0)
            credit = monoDelta
            val wallDelta = wallNow - saved.lastWall
            if (wallDelta > monoDelta + TOLERANCE_MS) flag = TimeFlag.ForwardJumpDetected
            else if (wallDelta < -TOLERANCE_MS) flag = TimeFlag.ClockWentBackwards
            verifiedNow = minOf(wallNow, verifiedBase(saved) + monoDelta + TOLERANCE_MS)
        } else {
            val mark = if (saved.maxCreditedWall > wallNow) saved.lastWall else saved.maxCreditedWall
            val start = maxOf(saved.lastWall, mark)
            if (wallNow <= start) {
                credit = 0
                if (wallNow < saved.lastWall && flag == TimeFlag.None) flag = TimeFlag.ClockWentBackwards
            } else credit = wallNow - start
            verifiedNow = wallNow
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
            maxCreditedWall = nextMark(saved.maxCreditedWall, verifiedNow, wallNow),
            suspicionCount = saved.suspicionCount + if (suspicious) 1 else 0,
        )
        return OfflineCredit(credit, flag, newClock, raw)
    }

    /** The last time we can vouch for: a wall reading that jumped ahead is not trusted beyond the mark. */
    private fun verifiedBase(saved: ClockState) = minOf(saved.lastWall, saved.maxCreditedWall)

    /** A mark ahead of the clock is dropped back to now; otherwise it rises only to verified time. */
    private fun nextMark(mark: Long, verifiedNow: Long, wallNow: Long): Long = maxOf(mark.coerceAtMost(wallNow), verifiedNow)

    /** Called on every save during live play: records "now" without crediting anything. */
    fun touch(saved: ClockState, clock: GameClock): ClockState {
        val wall = clock.wallMillis()
        val verified = if (clock.bootId() == saved.bootId) {
            val monoDelta = (clock.monotonicMillis() - saved.lastMono).coerceAtLeast(0)
            minOf(wall, verifiedBase(saved) + monoDelta + TOLERANCE_MS)
        } else wall
        return saved.copy(
            lastWall = wall,
            lastMono = clock.monotonicMillis(),
            bootId = clock.bootId(),
            maxCreditedWall = nextMark(saved.maxCreditedWall, verified, wall),
        )
    }

    fun initial(clock: GameClock) = ClockState(clock.wallMillis(), clock.monotonicMillis(), clock.bootId(), clock.wallMillis())
}
