package com.arnavgames.tycoon.core.engine.mechanics

import com.arnavgames.tycoon.core.content.ManagerPolicy
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.random.Pcg32
import com.arnavgames.tycoon.core.state.MechanicState

/**
 * Everything a mechanic may read. State only changes at minute boundaries ([MechanicModule.minute]) or through
 * player/manager actions, so income stays piecewise-constant and offline catch-up remains exactly equal to live play.
 */
class MechCtx(
    val businessId: String,
    val level: Int,
    val simTime: Long,
    val worldSeed: Long,
    /** Ids of every business the player owns (for cross-business synergies). */
    val owned: Set<String>,
    /**
     * One second of this business's gross income at efficiency 1.0. Module action costs are expressed in seconds
     * of income so they scale naturally with the business.
     */
    val incomeSecond: Money,
) {
    val minute: Long get() = simTime / 60_000

    /** Deterministic randomness for this business at a given key (e.g. a minute index or event counter). */
    fun rng(stream: String, key: Long): Pcg32 = GameRandom.stream(worldSeed, "mech:$businessId:$stream", key)

    fun owns(id: String) = id in owned

    /** Cost of an action worth [seconds] of this business's base income (never below $10). */
    fun cost(seconds: Double): Money = (incomeSecond * kotlin.math.max(1L, seconds.toLong())).max(Money.ofDollars(10))
}

/** Result of a player or manager action: the new state and the cash it costs (zero for free decisions). */
data class ActResult(val state: MechanicState.Module, val cost: Money = Money.ZERO)

sealed interface ActError {
    data class Invalid(val reason: String) : ActError
}

/** Description of a mechanic for the generic UI panel (the app renders it; no per-mechanic UI code needed). */
data class MechanicView(
    val title: String,
    val summary: String,
    val stats: List<ViewStat>,
    val controls: List<ViewControl>,
)

enum class Tone { Neutral, Good, Bad, Warn }

data class ViewStat(val label: String, val value: String, val tone: Tone = Tone.Neutral)

sealed interface ViewControl {
    /** Single choice; selecting an option sends action [actionId] with the option id as value. */
    data class Choice(val actionId: String, val title: String, val help: String, val options: List<ViewOption>, val selected: String?, val enabled: Boolean = true) : ViewControl

    /** A button; costs are shown and charged by the engine. */
    data class Action(val actionId: String, val title: String, val help: String, val label: String, val cost: Money?, val enabled: Boolean = true, val value: String = "") : ViewControl

    data class Progress(val title: String, val fraction: Double, val caption: String) : ViewControl
}

data class ViewOption(val id: String, val label: String, val detail: String)

/**
 * A business mechanic. Implementations are pure and deterministic.
 * Efficiency must be piecewise-constant between minute boundaries and actions.
 */
interface MechanicModule {
    val kind: String

    /** Parameter keys this module understands (validated against content). */
    val paramKeys: Set<String>

    fun initial(def: MechanicDef.Module, ctx: MechCtx): MechanicState.Module

    fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double

    fun upkeepFactor(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double = 1.0

    /** Advances one game minute (called at every minute boundary). */
    fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module = s

    /** A player action. Returns the new state and its cost, or an error. */
    fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult>

    /**
     * What a manager does each minute: free decisions plus small upkeep-style spending. [budget] is the most the
     * manager may spend this minute; the engine charges whatever cost is returned (never more than the budget).
     */
    fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult = ActResult(s)

    fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView
}

internal fun invalid(reason: String): Result<ActResult> = Result.failure(IllegalArgumentException(reason))

internal fun pct(x: Double) = "${(x * 100).toInt()}%"
