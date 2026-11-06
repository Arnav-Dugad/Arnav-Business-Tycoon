package com.arnavgames.tycoon.core.world

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.RivalDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.ModifierState
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure world formulas: rival fortunes and the Fortune List (GDD §10), and the timed income modifiers that world
 * events, decision cards and the Phoenix comeback boost apply to businesses (GDD §9, §11).
 */
class WorldMath(private val content: ContentPack, private val takeoverShield: (GameState) -> Double = { 0.0 }) {
    private val w get() = content.world
    private val sectorsOfBusiness: Map<String, Set<String>> = content.markets.sectors
        .flatMap { s -> s.businesses.map { it to s.id } }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    private val takeoverCards: Set<String> = content.world.cards.filter { it.kind == "takeover" }.map { it.id }.toSet()

    fun isUnlocked(state: GameState, feature: String) = state.progress.chapter >= (w.config.unlocks[feature] ?: 99)
    fun unlockChapter(feature: String) = w.config.unlocks[feature] ?: 99
    fun sectorsOf(businessId: String): Set<String> = sectorsOfBusiness[businessId] ?: emptySet()

    // ----- Rivals -----

    private fun phase(seed: Long, r: RivalDef): Double = GameRandom.stream(seed, "rival_phase", r.id.hashCode().toLong()).nextDouble() * 2 * PI

    /** log10 of a rival's net worth at sim time [simMs]. */
    fun rivalLog10(state: GameState, r: RivalDef, simMs: Long = state.simTimeMs): Double {
        val days = (simMs - generationStart(state)).coerceAtLeast(0) / 86_400_000.0
        val c = w.config.rivalCurve
        val g = c.earlyPerDay * minOf(days, c.kneeDays) + c.latePerDay * maxOf(0.0, days - c.kneeDays)
        return r.base + r.growth * g + r.wobble * sin(2 * PI * days / r.periodDays + phase(state.worldSeed, r))
    }

    /** Rival fortunes restart with every generation (Phase 10 sets this at prestige). */
    fun generationStart(state: GameState): Long = state.dynasty.generationStartSim

    fun rivalNetWorth(state: GameState, r: RivalDef, simMs: Long = state.simTimeMs): Money = fromLog10(rivalLog10(state, r, simMs))

    data class FortuneEntry(val rank: Int, val rivalId: String?, val log10: Double)

    /** The Fortune List: every rival plus the player (rivalId = null), richest first. */
    fun fortuneList(state: GameState, playerNetWorth: Money): List<FortuneEntry> {
        val player = log10(playerNetWorth)
        val all = w.rivals.map { it.id to rivalLog10(state, it) } + (null to player)
        return all.sortedByDescending { it.second }.mapIndexed { i, (id, lg) -> FortuneEntry(i + 1, id, lg) }
    }

    fun playerRank(state: GameState, playerNetWorth: Money): Int {
        val player = log10(playerNetWorth)
        return 1 + w.rivals.count { rivalLog10(state, it) > player }
    }

    fun relation(state: GameState, rivalId: String) = state.world.relations[rivalId] ?: 0

    fun hasAlly(state: GameState, except: String? = null): Boolean =
        state.world.relations.any { (id, v) -> id != except && v >= w.config.cards.allyRelation }

    // ----- Income modifiers -----

    /**
     * Product of all active modifiers that touch [businessId], and the takeover pressure from pending takeover cards.
     * businessRate is the engine's hottest function, so the per-business factors are memoised on the identity of the
     * modifier and card lists (both change only at events). The memo is one immutable snapshot.
     */
    fun incomeFactor(state: GameState, businessId: String): Double {
        val ws = state.world
        if (ws.modifiers.isEmpty() && ws.cards.isEmpty()) return 1.0
        val m = memo
        if (m != null && m.modifiers === ws.modifiers && m.cards === ws.cards) return m.factors[businessId] ?: 1.0
        val factors = HashMap<String, Double>()
        for (b in content.businesses) {
            var f = 1.0
            for (mod in ws.modifiers) if (touches(mod, b.id)) f *= (1.0 + mod.pct)
            if (ws.cards.any { it.business == b.id && it.card in takeoverCards }) f *= (1.0 - w.config.cards.takeoverPenalty * (1.0 - takeoverShield(state)).coerceIn(0.0, 1.0))
            if (f != 1.0) factors[b.id] = round6(f.coerceAtLeast(0.05))
        }
        memo = Memo(ws.modifiers, ws.cards, factors)
        return factors[businessId] ?: 1.0
    }

    private class Memo(val modifiers: List<ModifierState>, val cards: List<*>, val factors: Map<String, Double>)

    @Volatile private var memo: Memo? = null

    fun touches(mod: ModifierState, businessId: String): Boolean = when {
        mod.target == "all" -> true
        mod.target.startsWith("business:") -> mod.target.removePrefix("business:") == businessId
        mod.target.startsWith("sector:") -> mod.target.removePrefix("sector:") in sectorsOf(businessId)
        else -> false
    }

    /** Next modifier expiry after [now] (an engine boundary so income is integrated exactly), or null. */
    fun nextExpiry(state: GameState, now: Long): Long? = state.world.modifiers.filter { it.untilSim > now }.minOfOrNull { it.untilSim }

    /** Drops modifiers that have expired at [now]. */
    fun expire(state: GameState, now: Long): GameState {
        val mods = state.world.modifiers
        if (mods.none { it.untilSim <= now }) return state
        return state.copy(world = state.world.copy(modifiers = mods.filter { it.untilSim > now }))
    }

    companion object {
        private val MC = MathContext.DECIMAL64
        fun round6(x: Double): Double = kotlin.math.floor(x * 1_000_000.0 + 0.5) / 1_000_000.0

        fun log10(m: Money): Double {
            val d = m.toDollarsDouble()
            return if (d <= 1.0) 0.0 else kotlin.math.log10(d)
        }

        /** 10^x dollars as exact Money (3 significant digits are plenty for a fortune). */
        fun fromLog10(x: Double): Money {
            val exp = kotlin.math.floor(x).toInt()
            val mant = Math.pow(10.0, x - exp)
            val dollars = BigDecimal(mant, MC).setScale(6, java.math.RoundingMode.HALF_EVEN).movePointRight(exp)
            return Money.ofDollars(dollars)
        }
    }
}
