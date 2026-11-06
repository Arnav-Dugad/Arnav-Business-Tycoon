package com.arnavgames.tycoon.core.progression

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.WeeklyEventDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import kotlin.math.floor
import kotlin.math.pow

/**
 * Pure Dynasty formulas (docs/ECONOMY.md §9): Legacy and its income multiplier, Family Charter and heir-trait bonuses,
 * the weekly event, Dynasty challenge rules and the IPO float.
 */
class DynastyMath(private val content: ContentPack) {
    private val p get() = content.progression
    private val cfg get() = p.config
    private val sectorsOfBusiness: Map<String, Set<String>> = content.markets.sectors
        .flatMap { s -> s.businesses.map { it to s.id } }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    // ----- Time -----

    fun day(simMs: Long) = simMs / DAY
    fun week(simMs: Long) = simMs / (cfg.weekDays * DAY)
    fun season(simMs: Long) = simMs / (cfg.seasonDays * DAY)

    /** This week's event: a seeded pick from the templates (a different one each week, stable per world). */
    fun weeklyEvent(state: GameState): WeeklyEventDef? {
        // Weekly events begin with the Operator phase (chapter 3), once the early story has settled in.
        if (p.weeklyEvents.isEmpty() || state.progress.chapter < 3) return null
        val w = week(state.simTimeMs)
        val i = GameRandom.stream(state.worldSeed, "weekly_event", w).nextInt(p.weeklyEvents.size)
        return p.weeklyEvents[i]
    }

    // ----- Bonuses -----

    /** Sum of every active bonus of [type]: Family Charter, heir trait and the weekly event. */
    fun bonus(state: GameState, type: String): Double {
        val d = state.dynasty
        var total = 0.0
        if (d.charter.isNotEmpty()) for (id in d.charter) p.charterById[id]?.let { if (it.effect == type) total += it.value }
        d.trait?.let { t -> p.traitById[t]?.let { if (it.effect == type) total += it.value } }
        weeklyEvent(state)?.let { e -> if (e.effect == type) total += e.value }
        return total
    }

    fun legacyMultiplier(legacy: Long): Double = if (legacy <= 0) 1.0 else 1.0 + cfg.legacyIncomeFactor * legacy.toDouble().pow(cfg.legacyIncomeExponent)

    /**
     * Income factor for a business: Legacy × (1 + all-business bonuses) × (1 + sector bonuses) × (1 − IPO float).
     * Memoised per (dynasty state, week, chapter) because businessRate is the hottest function in the engine.
     */
    fun incomeFactor(state: GameState, businessId: String): Double {
        val d = state.dynasty
        val wk = week(state.simTimeMs)
        val m = memo
        val wkKey = if (state.progress.chapter < 3) -1L else wk
        if (m != null && m.dynasty === d && m.week == wkKey && m.seed == state.worldSeed) return m.factors[businessId] ?: 1.0
        val legacy = legacyMultiplier(d.legacy)
        val all = 1.0 + bonus(state, "incomeAll")
        val float = 1.0 - (d.ipo?.float ?: 0.0)
        val factors = HashMap<String, Double>()
        for (b in content.businesses) {
            var f = legacy * all * float
            for (sec in sectorsOfBusiness[b.id] ?: emptySet()) f *= 1.0 + bonus(state, "incomeSector:$sec") + bonus(state, "sector:$sec")
            factors[b.id] = round6(f)
        }
        memo = Memo(d, wkKey, state.worldSeed, factors)
        return factors[businessId] ?: 1.0
    }

    private class Memo(val dynasty: Any, val week: Long, val seed: Long, val factors: Map<String, Double>)

    @Volatile private var memo: Memo? = null

    // ----- Challenges -----

    fun rule(state: GameState): String? = state.dynasty.challenge?.let { p.challengeById[it]?.rule }

    fun forbids(state: GameState, rule: String) = rule(state) == rule

    /** Tax rate after charter/trait cuts and the Tax-hawk challenge. */
    fun adjustTax(state: GameState, rate: Double): Double {
        if (rate <= 0.0) return rate
        val plus = if (forbids(state, "taxPlus")) 0.05 else 0.0
        return round6((rate - bonus(state, "taxCut") + plus).coerceIn(0.0, 0.6))
    }

    // ----- Prestige -----

    /** The chapter 8 finale is complete: the founder may pass the crown. */
    fun canPrestige(state: GameState): Boolean {
        val last = content.chapters.maxByOrNull { it.number } ?: return false
        return last.missions.isNotEmpty() && last.missions.all { it.id in state.progress.completedMissions }
    }

    /** Legacy earned by passing the crown now (before the Legacy bonuses and challenge bonus). */
    fun baseLegacyGain(peakNetWorth: Money): Long {
        val x = peakNetWorth.toDollarsDouble() / 10.0.pow(LEGACY_NORMALIZER_LOG10)
        if (x <= 0) return 0
        return floor(cfg.legacyScale * x.pow(cfg.legacyExponent)).toLong()
    }

    fun legacyGain(state: GameState): Long {
        val base = baseLegacyGain(state.stats.peakNetWorth)
        val challenge = state.dynasty.challenge?.let { p.challengeById[it]?.legacyBonus } ?: 0.0
        return floor(base * (1.0 + bonus(state, "legacyBonus") + challenge)).toLong()
    }

    fun heirloomsAllowed(generation: Int): Int = cfg.heirlooms.getOrElse(generation - 1) { cfg.heirlooms.lastOrNull() ?: 1 }

    /** Three traits offered to the next heir, seeded by world and generation. */
    fun traitOffer(state: GameState): List<String> {
        val r = GameRandom.stream(state.worldSeed, "heir_traits", state.dynasty.generation.toLong())
        val pool = p.traits.map { it.id }.toMutableList()
        return List(minOf(cfg.traitChoices, pool.size)) { pool.removeAt(r.nextInt(pool.size)) }
    }

    /** Charter nodes available to a generation with [legacy] banked. */
    fun charterUnlocked(legacy: Long, generation: Int) = p.charter.filter { legacy >= it.legacy && generation >= it.generation }

    companion object {
        const val DAY = 86_400_000L
        /** Peak net worth is measured in units of $10^18 (Chapter 8 needs about 4×10^21, giving ~130 Legacy). */
        const val LEGACY_NORMALIZER_LOG10 = 18
        fun round6(x: Double): Double = floor(x * 1_000_000.0 + 0.5) / 1_000_000.0
    }
}
