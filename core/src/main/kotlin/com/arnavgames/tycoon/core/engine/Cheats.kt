package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

/**
 * The sandbox cheats offered in Settings. They change only this save, and the first use marks it as a sandbox for
 * good ([isSandbox]): sandbox saves never post to leaderboards, so cheats can't distort anyone else's game.
 */
object Cheats {
    const val CASH_HOUR = "cash_hour"
    const val CASH_DAY = "cash_day"
    const val CASH_DOUBLE = "cash_double"
    const val BULLION = "bullion"
    const val FAVOR = "favor"
    const val SKIP_HOUR = "skip_hour"
    const val SKIP_8H = "skip_8h"
    const val FINISH_TIMERS = "finish_timers"
    const val FRENZY = "frenzy"
    const val LEVELS = "levels"
    val KINDS = setOf(CASH_HOUR, CASH_DAY, CASH_DOUBLE, BULLION, FAVOR, SKIP_HOUR, SKIP_8H, FINISH_TIMERS, FRENZY, LEVELS)

    /** How many cheats this save has ever used (0 = a clean save). */
    fun uses(s: GameState): Long = s.meta.counters["cheats"] ?: 0

    fun isSandbox(s: GameState): Boolean = uses(s) > 0

    /** Income over [seconds] at today's full rate (managed and unmanaged businesses). */
    fun incomeOver(engine: GameEngine, s: GameState, seconds: Long): Money = engine.economy.totalNetRate(s, managedOnly = false).over(seconds)
}
