package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.Serializable

/**
 * Long-term goals that survive prestige (docs/GDD.md §12): lifetime counters, daily and weekly missions,
 * achievements, the daily reward ladder and the season track.
 */
@Serializable
data class MetaState(
    /** Which economy the save's money is in: 1 = the idle-game scale (before 1.8.0), 2 = real-world money. */
    val economy: Int = 1,
    /** Lifetime counters by metric (levels, taps, cards, trades…), across generations. */
    val counters: Map<String, Long> = emptyMap(),
    val daily: List<MissionState> = emptyList(),
    val weekly: List<MissionState> = emptyList(),
    /** Game-day index the daily missions were assigned for (-1 = none yet). */
    val dailyDay: Long = -1,
    val weekIndex: Long = -1,
    /** Game day of the last free reroll. */
    val rerollDay: Long = -1,
    val achievements: Set<String> = emptySet(),
    val achievementsClaimable: Set<String> = emptySet(),
    /** Daily reward ladder: steps claimed so far (the next is step % 7 + 1) and the last game day claimed. */
    val ladderClaimed: Long = 0,
    val ladderLastDay: Long = -1,
    val seasonIndex: Long = -1,
    val seasonXp: Long = 0,
    val seasonClaimedFree: Set<Int> = emptySet(),
    val seasonClaimedPremium: Set<Int> = emptySet(),
    /** Weekly event goals finished this week ("<eventId>:<metric>"), with their baselines. */
    val eventGoals: List<MissionState> = emptyList(),
    /** Game day the Market Timing practice bonus was last collected (once a day). */
    val timingDay: Long = -1,
)

@Serializable
data class MissionState(
    val template: String,
    val metric: String,
    val target: Long,
    /** For "earn_hours": the dollar target; baseline is lifetime earnings when assigned. */
    val targetMoney: Money = Money.ZERO,
    val baseline: Long = 0,
    val baselineMoney: Money = Money.ZERO,
    val rewardCash: Money = Money.ZERO,
    val bullion: Long = 0,
    val seasonXp: Long = 0,
    val claimed: Boolean = false,
)

/** The Dynasty (docs/GDD.md §12): generations, Legacy, Family Charter, heir trait, challenges and the IPO. */
@Serializable
data class DynastyState(
    val generation: Int = 1,
    /** Total Legacy banked over all generations. */
    val legacy: Long = 0,
    val charter: Set<String> = emptySet(),
    val trait: String? = null,
    val challenge: String? = null,
    val challengesDone: Set<String> = emptySet(),
    val heirloomsPassed: Int = 0,
    /** Sim time the current generation started (rival fortunes restart from here). */
    val generationStartSim: Long = 0,
    /** Traits offered to the next heir (seeded at the first chance to prestige). */
    val traitOffer: List<String> = emptyList(),
    val ipo: IpoState? = null,
    val founderNames: List<String> = emptyList(),
)

/** Demo-store entitlements (docs/GDD.md §15). They are permanent: they survive prestige. */
@Serializable
data class StoreState(
    /** The player has used a demo (simulated real-money) purchase: shown on leaderboards. */
    val boosted: Boolean = false,
    val demoPurchases: Int = 0,
    /** Product id -> times bought. */
    val purchases: Map<String, Int> = emptyMap(),
    val permanentPct: Double = 0.0,
    val extraSlots: Int = 0,
    val offlineHours: Int = 0,
    val vipUntil: Long = 0,
    /** Season indices whose premium track is unlocked. */
    val seasonPasses: Set<Long> = emptySet(),
    val cosmetics: Set<String> = emptySet(),
    val title: String? = null,
    val frame: String? = null,
    /** Profile avatar: a free avatar id from store.json or an owned avatar cosmetic (null = the default crown). */
    val avatar: String? = null,
)

@Serializable
data class IpoState(val float: Double, val listedAtSim: Long, val raised: Money)
