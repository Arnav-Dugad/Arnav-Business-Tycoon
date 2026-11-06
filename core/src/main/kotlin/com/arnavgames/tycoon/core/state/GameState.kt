package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.CoreInfo
import com.arnavgames.tycoon.core.ledger.LedgerState
import com.arnavgames.tycoon.core.money.Accrual
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.time.ClockState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The complete, immutable game state. Everything needed to continue a game is in here; it is what gets saved.
 * All engine time is `simTimeMs` (game milliseconds since the world began), advanced by live play and offline credit alike.
 */
@Serializable
data class GameState(
    val schema: Int = CoreInfo.SAVE_SCHEMA_VERSION,
    val worldSeed: Long,
    val contentHash: String,
    val createdAtWall: Long,
    val simTimeMs: Long = 0,
    val clock: ClockState,
    val ledger: LedgerState = LedgerState(),
    val businesses: Map<String, BusinessState> = emptyMap(),
    val hq: HqState = HqState(),
    val hustle: HustleState = HustleState(),
    val timers: List<TimerState> = emptyList(),
    val progress: ProgressState = ProgressState(),
    val stats: StatsState = StatsState(),
    val nextTimerId: Long = 1,
    /** Merger tree: formed conglomerate ids, and the holding company name once formed. */
    val conglomerates: Set<String> = emptySet(),
    val holdingName: String? = null,
    val market: MarketState = MarketState(),
) {
    val cash: Money get() = ledger.cash
}

@Serializable
data class BusinessState(
    val id: String,
    val level: Int = 1,
    val managerId: String? = null,
    /** Output waiting to be collected (unmanaged businesses only). */
    val stored: Money = Money.ZERO,
    val accrual: Accrual = Accrual(),
    val expansionsDone: Set<String> = emptySet(),
    val mothballed: Boolean = false,
    val mechanic: MechanicState = MechanicState.None,
    val openedAtSim: Long = 0,
)

/** Mechanic-specific state; one subtype per mechanic module. */
@Serializable
sealed interface MechanicState {
    @Serializable @SerialName("none") data object None : MechanicState

    @Serializable @SerialName("retail")
    data class Retail(
        /** Price as a percentage of the reference price. */
        val pricePct: Int = 100,
        /** Promotion active until this sim time (0 = none). */
        val promoUntil: Long = 0,
    ) : MechanicState

    @Serializable @SerialName("restaurant")
    data class Restaurant(
        /** Reputation in milli-stars (1000..5000) so it stays exact across splits. */
        val milliStars: Int,
        val qualityTier: String,
    ) : MechanicState

    @Serializable @SerialName("logistics")
    data class Logistics(
        /** Vehicles assigned per route id. Unassigned vehicles sit in the depot. */
        val assignments: Map<String, Int> = emptyMap(),
    ) : MechanicState

    /**
     * State for module mechanics (core.engine.mechanics). Numbers are Longs (minute indices, counters, milli-units)
     * so the state stays exact and serializes cleanly; each module wraps it in typed accessors.
     */
    @Serializable @SerialName("module")
    data class Module(
        val kind: String,
        val nums: Map<String, Long> = emptyMap(),
        val strs: Map<String, String> = emptyMap(),
    ) : MechanicState {
        fun n(key: String, default: Long = 0): Long = nums[key] ?: default
        fun s(key: String, default: String = ""): String = strs[key] ?: default
        fun with(vararg pairs: Pair<String, Long>) = copy(nums = nums + pairs)
        fun withS(vararg pairs: Pair<String, String>) = copy(strs = strs + pairs)
    }
}

@Serializable
data class HqState(val level: Int = 1)

@Serializable
data class HustleState(
    val combo: Int = 0,
    val lastTapSim: Long = Long.MIN_VALUE / 2,
    val totalTaps: Long = 0,
    val frenzyUntil: Long = 0,
    val frenzyChargesUsed: Int = 0,
    /** Game-day index (simTime / 24h) the charges were counted for. */
    val frenzyDay: Long = 0,
)

@Serializable
data class TimerState(
    val id: Long,
    val kind: TimerKind,
    val targetId: String,
    val detailId: String = "",
    val startSim: Long,
    val endSim: Long,
)

@Serializable
enum class TimerKind { Expansion }

@Serializable
data class ProgressState(
    val chapter: Int = 1,
    val completedMissions: Set<String> = emptySet(),
    /** Missions completed whose reward has not been claimed yet. */
    val claimableMissions: Set<String> = emptySet(),
    val onboardingStep: Int = 0,
)

@Serializable
data class StatsState(
    val peakNetWorth: Money = Money.ZERO,
    val lifetimeEarned: Money = Money.ZERO,
    val levelsBought: Long = 0,
    val playSessions: Long = 0,
)
