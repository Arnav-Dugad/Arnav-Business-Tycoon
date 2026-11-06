package com.arnavgames.tycoon.sim

import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Mechanics
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.money.Rate
import com.arnavgames.tycoon.core.random.Pcg32
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import com.arnavgames.tycoon.core.state.TimerKind

/** How a bot spends its time (docs/ECONOMY.md §10.1). Real minutes. */
data class PlaySchedule(
    /** Session start times within a day, in minutes after midnight. */
    val sessionStarts: List<Int>,
    val sessionMinutes: Int,
    /** The very first session (install day) is usually longer. */
    val firstSessionMinutes: Int,
    /** Hustle taps per second while the bot is actively tapping. */
    val tapsPerSecond: Double,
    /** Taps only during the first N minutes of each session (0 = never tap). */
    val tapMinutesPerSession: Int,
    /** Extra check-ins on install day (minutes after install): new players come back a few times on day one. */
    val day0ExtraSessions: List<Int> = emptyList(),
)

/** A player policy. Bots only use the public Command API, exactly like the app. */
interface Bot {
    val name: String
    val schedule: PlaySchedule

    /** Called every decision step during a session. Returns the new state. */
    fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean): GameState
}

/** Applies a command if accepted; otherwise returns the state unchanged. */
internal fun GameEngine.tryApply(s: GameState, cmd: Command): GameState =
    (apply(s, cmd) as? CommandResult.Ok)?.state ?: s

/** Shared decision helpers: candidate investments ranked by payback (cost ÷ income gained). */
internal class Planner(private val engine: GameEngine) {
    private val eco = engine.economy

    data class Option(val cmd: Command, val cost: Money, val gain: Rate, val label: String) {
        /** Seconds of the gained income needed to repay the cost (∞ if no gain). */
        val paybackSeconds: Double get() = if (gain.isZero) Double.POSITIVE_INFINITY else cost.ratioTo(gain.perSecond().max(Money.ONE_CENT))
    }

    private fun rate(s: GameState, managedOnly: Boolean): Rate = eco.totalNetRate(s, managedOnly)

    private fun gainOf(s: GameState, cmd: Command, managedOnly: Boolean): Pair<GameState, Rate>? {
        val r = engine.apply(s, cmd) as? CommandResult.Ok ?: return null
        val gain = rate(r.state, managedOnly) - rate(s, managedOnly)
        return r.state to gain
    }

    /** All affordable growth options right now. [managedOnly] values only income that keeps flowing offline. */
    fun options(s: GameState, managedOnly: Boolean): List<Option> {
        val out = mutableListOf<Option>()
        val content = engine.content
        for (b in s.businesses.values) {
            val def = content.business(b.id)
            val cost = eco.levelCost(def, b.level, 1)
            if (cost <= s.cash) gainOf(s, Command.BuyLevels(b.id, 1), managedOnly)?.let { (_, g) -> out += Option(Command.BuyLevels(b.id, 1), cost, g, "level ${b.id}") }
            for (e in def.expansions) {
                if (e.id in b.expansionsDone || s.timers.any { it.targetId == b.id } || b.level < e.requiresLevel || e.cost > s.cash) continue
                // Value an expansion by the income it adds once complete.
                val done = s.copy(businesses = s.businesses + (b.id to b.copy(expansionsDone = b.expansionsDone + e.id)))
                val g = rate(done, managedOnly) - rate(s, managedOnly)
                out += Option(Command.StartExpansion(b.id, e.id), e.cost, g, "expansion ${e.id}")
            }
        }
        for (def in content.businesses) {
            if (def.id in s.businesses || !engine.isUnlocked(s, def.id) || def.unlockCost > s.cash) continue
            if (eco.usedSlots(s) >= eco.slots(s)) continue
            gainOf(s, Command.OpenBusiness(def.id), managedOnly)?.let { (_, g) -> out += Option(Command.OpenBusiness(def.id), def.unlockCost, g, "open ${def.id}") }
        }
        return out
    }

    /** Cheapest manager that is an upgrade over the current one, if affordable. */
    fun managerOption(s: GameState): Pair<Command, Money>? {
        for (b in s.businesses.values.sortedBy { it.id }) {
            val def = engine.content.business(b.id)
            val currentStars = def.managers.firstOrNull { it.id == b.managerId }?.stars ?: 0
            val next = def.managers.filter { it.stars > currentStars }.minByOrNull { it.cost } ?: continue
            if (next.cost <= s.cash) return Command.HireManager(b.id, next.id) to next.cost
        }
        return null
    }

    /** First manager for an unmanaged business (the most important purchase for offline income). */
    fun firstManager(s: GameState): Pair<Command, Money>? = s.businesses.values
        .filter { it.managerId == null }
        .mapNotNull { b ->
            val m = engine.content.business(b.id).managers.minBy { it.cost }
            if (m.cost <= s.cash) Command.HireManager(b.id, m.id) to m.cost else null
        }
        .minByOrNull { it.second }

    /** Plays each unmanaged business's mechanic as well as a skilled player would. */
    fun tuneMechanics(s: GameState, skill: Double): GameState {
        var st = s
        for (b in s.businesses.values) {
            if (b.managerId != null) continue
            val def = engine.content.business(b.id)
            when (val m = def.mechanic) {
                is MechanicDef.Retail -> {
                    val target = (100 + (Mechanics.retailOptimalPricePct(m) - 100) * skill).toInt() / 5 * 5
                    val cur = (b.mechanic as? MechanicState.Retail)?.pricePct
                    if (cur != target) st = engine.tryApply(st, Command.SetRetailPrice(b.id, target.coerceIn(m.minPricePct, m.maxPricePct)))
                }
                is MechanicDef.Restaurant -> {
                    val best = m.qualityTiers.maxBy { Mechanics.restaurantTierValue(m, it.id, def.upkeep.toDouble()) }.id
                    val cur = (b.mechanic as? MechanicState.Restaurant)?.qualityTier
                    if (skill >= 0.5 && cur != best) st = engine.tryApply(st, Command.SetRestaurantQuality(b.id, best))
                }
                is MechanicDef.Logistics -> {
                    val plan = Mechanics.logisticsPlan(m, b.level, skill)
                    val cur = (b.mechanic as? MechanicState.Logistics)?.assignments
                    if (cur != plan) {
                        // Clear then assign, route by route, via the public command.
                        for (r in Mechanics.unlockedRoutes(m, b.level)) st = engine.tryApply(st, Command.AssignVehicles(b.id, r.id, 0))
                        for ((route, n) in plan) st = engine.tryApply(st, Command.AssignVehicles(b.id, route, n))
                    }
                }
                MechanicDef.Simple -> Unit
            }
        }
        return st
    }

    fun claimAll(s: GameState): GameState = s.progress.claimableMissions.sorted().fold(s) { acc, id -> engine.tryApply(acc, Command.ClaimMission(id)) }

    fun hqIfUseful(s: GameState, slotsFull: Boolean, incomePerSec: Money, horizonSec: Long): GameState {
        val next = engine.content.balance.hqLevels.firstOrNull { it.level == s.hq.level + 1 } ?: return s
        if (next.cost > s.cash) return s
        val cheap = next.cost <= incomePerSec * horizonSec
        return if (slotsFull || cheap) engine.tryApply(s, Command.UpgradeHq) else s
    }
}

// ---------------------------------------------------------------------------------------------
// The bots
// ---------------------------------------------------------------------------------------------

/** Engaged player: buys the best payback first, plays mechanics well, taps early on. */
class ActiveBot(override val name: String = "active", private val reserveFraction: Double = 0.0, private val promos: Boolean = true) : Bot {
    override val schedule = PlaySchedule(sessionStarts = listOf(8 * 60, 13 * 60, 20 * 60), sessionMinutes = 15, firstSessionMinutes = 25, tapsPerSecond = 3.0, tapMinutesPerSession = 5, day0ExtraSessions = listOf(90, 210))

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean): GameState {
        val p = Planner(engine)
        var s = p.claimAll(state)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        s = engine.tryApply(s, Command.CollectAll)
        if (sessionStart) s = engine.tryApply(s, Command.ActivateFrenzy)
        s = p.tuneMechanics(s, skill = 1.0)
        val income = engine.economy.totalNetRate(s).perSecond()
        // Managers keep money flowing offline; an engaged player hires one as soon as it pays back within ~20 minutes.
        p.firstManager(s)?.let { (cmd, cost) -> if (cost <= income * 1200 || cost <= s.cash * Multiplier.of("0.5")) s = engine.tryApply(s, cmd) }
        s = p.hqIfUseful(s, slotsFull = engine.economy.usedSlots(s) >= engine.economy.slots(s), incomePerSec = income, horizonSec = 600)
        // Greedy: keep buying the fastest payback until money runs out (respecting any reserve).
        repeat(400) {
            val reserve = s.cash * Multiplier.ofDouble(reserveFraction)
            val best = p.options(s, managedOnly = false).filter { s.cash - it.cost >= reserve }.minByOrNull { it.paybackSeconds } ?: return@repeat
            if (best.paybackSeconds > 6 * 3600) return@repeat
            val ns = engine.tryApply(s, best.cmd)
            if (ns === s) return@repeat
            s = ns
        }
        // Better managers once they are cheap relative to income.
        p.managerOption(s)?.let { (cmd, cost) -> if (cost <= income * 300) s = engine.tryApply(s, cmd) }
        if (promos) {
            val retail = s.businesses["retail"]
            if (retail != null && retail.managerId == null && (retail.mechanic as? MechanicState.Retail)?.promoUntil ?: 0 <= s.simTimeMs) {
                val cost = engine.promoCost(s, "retail")
                if (cost <= s.cash * Multiplier.of("0.2")) s = engine.tryApply(s, Command.StartRetailPromo("retail"))
            }
        }
        return s
    }
}

/** Checks in briefly twice a day: collects, hires managers, buys the obvious upgrades. Never taps after day one. */
class IdleBot : Bot {
    override val name = "idle"
    override val schedule = PlaySchedule(sessionStarts = listOf(9 * 60, 21 * 60), sessionMinutes = 3, firstSessionMinutes = 10, tapsPerSecond = 2.0, tapMinutesPerSession = 2, day0ExtraSessions = listOf(60, 240))

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean): GameState {
        val p = Planner(engine)
        var s = p.claimAll(state)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        s = engine.tryApply(s, Command.CollectAll)
        p.firstManager(s)?.let { (cmd, _) -> s = engine.tryApply(s, cmd) }
        // Idle players use the free auto-plan button rather than fine-tuning.
        for (b in s.businesses.values) if (engine.content.business(b.id).mechanic is MechanicDef.Logistics && b.managerId == null) s = engine.tryApply(s, Command.AutoPlanRoutes(b.id))
        val income = engine.economy.totalNetRate(s, managedOnly = true).perSecond()
        s = p.hqIfUseful(s, slotsFull = engine.economy.usedSlots(s) >= engine.economy.slots(s), incomePerSec = income, horizonSec = 1800)
        repeat(400) {
            // Idle players buy the obvious best-value upgrade (they hire managers above, so most income keeps flowing).
            val best = p.options(s, managedOnly = false).minByOrNull { it.paybackSeconds } ?: return@repeat
            if (best.paybackSeconds > 12 * 3600) return@repeat
            val ns = engine.tryApply(s, best.cmd)
            if (ns === s) return@repeat
            s = ns
        }
        p.managerOption(s)?.let { (cmd, cost) -> if (cost <= income * 600) s = engine.tryApply(s, cmd) }
        return s
    }
}

/** Makes poor choices: random affordable purchases, never tunes mechanics, overspends on managers. */
class PoorChoicesBot : Bot {
    override val name = "poor_choices"
    override val schedule = PlaySchedule(sessionStarts = listOf(10 * 60, 18 * 60), sessionMinutes = 8, firstSessionMinutes = 12, tapsPerSecond = 1.5, tapMinutesPerSession = 2, day0ExtraSessions = listOf(90, 300))

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean): GameState {
        var s = state
        if (rng.chance(0.5)) s = Planner(engine).claimAll(s)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        if (rng.chance(0.6)) s = engine.tryApply(s, Command.CollectAll)
        repeat(rng.nextInt(1, 6)) {
            val ids = s.businesses.keys.sorted()
            val cmd: Command = when (rng.nextInt(6)) {
                0 -> engine.content.businesses.map { it.id }.let { Command.OpenBusiness(rng.pick(it)) }
                1 -> if (ids.isEmpty()) Command.CollectAll else rng.pick(ids).let { id -> Command.HireManager(id, rng.pick(engine.content.business(id).managers).id) }
                2 -> if (ids.isEmpty()) Command.CollectAll else rng.pick(ids).let { id -> Command.StartExpansion(id, rng.pick(engine.content.business(id).expansions).id) }
                3 -> Command.UpgradeHq
                // A poor player sets prices badly when they touch them at all.
                4 -> Command.SetRetailPrice("retail", rng.pick(listOf(80, 85, 180, 200)))
                else -> if (ids.isEmpty()) Command.CollectAll else Command.BuyLevels(rng.pick(ids), rng.nextInt(1, 4))
            }
            s = engine.tryApply(s, cmd)
        }
        return s
    }
}

/** Plays like the active bot but keeps a 30% cash buffer and avoids promotions. */
fun cautiousBot() = ActiveBot(name = "cautious", reserveFraction = 0.30, promos = false)

/** Timers are serviced by the simulation loop; exposed for reports. */
internal fun GameState.expansionRunning() = timers.any { it.kind == TimerKind.Expansion }
