package com.arnavgames.tycoon.sim

import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.engine.Mechanics
import com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry
import com.arnavgames.tycoon.core.engine.mechanics.ViewControl
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
    fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean = false): GameState
}

/** Applies a command if accepted; otherwise returns the state unchanged. Counts meaningful decisions (see [Decisions]). */
internal fun GameEngine.tryApply(s: GameState, cmd: Command): GameState {
    val ns = (apply(s, cmd) as? CommandResult.Ok)?.state ?: return s
    if (Decisions.isDecision(cmd) && Decisions.changedSomething(s, ns)) Decisions.count.get()[0]++
    return ns
}

/**
 * Meaningful decisions for the "idle minutes" health metric (docs/PLAN_2.0.md A0): choices with alternatives that
 * change the game. Routine actions (tapping, collecting, claiming rewards) and the bots' automatic cash parking in
 * the index fund are not decisions; purchases of levels, businesses, HQ and expansions are counted separately.
 */
internal object Decisions {
    /** Per simulation thread: [0] = decisions made so far. */
    val count: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(1) }

    fun isDecision(cmd: Command): Boolean = when (cmd) {
        is Command.HustleTap, Command.CollectAll, is Command.Collect, is Command.BuyLevels, is Command.OpenBusiness,
        Command.UpgradeHq, is Command.StartExpansion, is Command.ClaimMission, Command.ClaimDailyReward,
        is Command.ClaimDaily, is Command.ClaimWeekly, is Command.ClaimAchievement, is Command.ClaimSeasonTier,
        is Command.AdvanceOnboarding -> false
        is Command.BuyInstrument -> cmd.instrumentId != FUND
        is Command.SellInstrument -> cmd.instrumentId != FUND
        else -> true
    }

    /** Re-applying an identical setting (e.g. the same price every step) is not a new decision. */
    fun changedSomething(a: GameState, b: GameState): Boolean =
        a.businesses != b.businesses || a.market != b.market || a.estate != b.estate || a.luxury != b.luxury ||
            a.world != b.world || a.finance != b.finance || a.conglomerates != b.conglomerates || a.timers != b.timers
}

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
            // Buying straight to the next Scale Tier captures its multiplier, often a far better deal than one level.
            eco.nextTier(def, b.level)?.let { tier ->
                val n = tier.level - b.level
                if (n > 1) {
                    val tierCost = eco.levelCost(def, b.level, n)
                    if (tierCost <= s.cash) gainOf(s, Command.BuyLevels(b.id, n), managedOnly)?.let { (_, g) -> out += Option(Command.BuyLevels(b.id, n), tierCost, g, "tier ${b.id}") }
                }
            }
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

    /** Opens newly unlocked businesses as soon as they are affordable: new content is its own reward for players. */
    /**
     * The opening price of a business the current chapter asks the player to open (Next Move card), if it is unlocked,
     * not owned yet and reachable within [horizonSec] of income. Players save for these instead of spending greedily.
     */
    fun storyTarget(s: GameState, incomePerSec: Money, horizonSec: Long): Money? {
        val ch = engine.content.chapters.firstOrNull { it.number == s.progress.chapter } ?: return null
        val open = ch.missions.filter { it.id !in s.progress.completedMissions }
        // Only when the story has nothing else to do: every open goal is about a business not owned yet.
        fun aboutUnowned(g: com.arnavgames.tycoon.core.content.GoalDef) = when (g) {
            is com.arnavgames.tycoon.core.content.GoalDef.OwnBusiness -> g.businessId !in s.businesses
            is com.arnavgames.tycoon.core.content.GoalDef.BusinessLevel -> g.businessId !in s.businesses
            is com.arnavgames.tycoon.core.content.GoalDef.HireManager -> g.businessId !in s.businesses
            else -> false
        }
        if (open.isEmpty() || !open.all { aboutUnowned(it.goal) }) return null
        return open.asSequence()
            .mapNotNull { (it.goal as? com.arnavgames.tycoon.core.content.GoalDef.OwnBusiness)?.businessId }
            .filter { it !in s.businesses && engine.isUnlocked(s, it) }
            .map { engine.content.business(it).unlockCost }
            .filter { it <= s.cash + incomePerSec * horizonSec }
            .minOrNull()
    }

    /**
     * Levels the current chapter asks for ("Get Telecom to level 25"), bought in one go when affordable: what a player
     * does after tapping "Show me" on the Goals tab, even when those levels are poor value on their own.
     */
    fun missionLevels(s: GameState): GameState {
        val ch = engine.content.chapters.firstOrNull { it.number == s.progress.chapter } ?: return s
        var st = s
        for (mi in ch.missions) {
            if (mi.id in st.progress.completedMissions) continue
            val g = mi.goal as? com.arnavgames.tycoon.core.content.GoalDef.BusinessLevel ?: continue
            val b = st.businesses[g.businessId] ?: continue
            if (b.level >= g.level) continue
            val cost = eco.levelCost(engine.content.business(b.id), b.level, g.level - b.level)
            if (cost <= st.cash) st = engine.tryApply(st, Command.BuyLevels(b.id, g.level - b.level))
        }
        return st
    }

    fun openNewBusinesses(s: GameState, maxShareOfCash: Double): GameState {
        var st = s
        for (def in engine.content.businesses) {
            if (def.id in st.businesses || !engine.isUnlocked(st, def.id)) continue
            if (def.unlockCost > st.cash * Multiplier.ofDouble(maxShareOfCash)) continue
            if (eco.usedSlots(st) >= eco.slots(st)) {
                // Slots full: mothball the weakest running business to make room (a sensible player's move).
                val weakest = st.businesses.values.filter { !it.mothballed }.minByOrNull { eco.businessRate(st, it.id).net } ?: continue
                st = engine.tryApply(st, Command.SetMothballed(weakest.id, true))
            }
            st = engine.tryApply(st, Command.OpenBusiness(def.id))
        }
        return st
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
                is MechanicDef.Module -> st = tuneModule(st, b.id, m, skill)
                MechanicDef.Simple -> Unit
            }
        }
        return st
    }

    /**
     * Generic optimizer for module mechanics, driven by the module's own view: for every choice, try each option on
     * a copy of the state and keep whichever earns the most right now. A player with [skill] < 1 sometimes skips this.
     */
    private fun tuneModule(s: GameState, id: String, m: MechanicDef.Module, skill: Double): GameState {
        val def = engine.content.business(id)
        val b = s.businesses.getValue(id)
        val view = MechanicRegistry.of(m.kind).view(m, engine.economy.moduleState(s, def, b, m), engine.economy.mechCtx(s, def, b))
        var st = s
        for (choice in view.controls.filterIsInstance<ViewControl.Choice>()) {
            if (!choice.enabled || choice.actionId == "campaign") continue
            if (skill < 1.0 && (st.simTimeMs / 60_000) % 10 >= (skill * 10).toLong()) continue
            var best = st
            var bestRate = eco.businessRate(st, id).net
            for (o in choice.options) {
                val r = engine.apply(st, Command.MechanicAction(id, choice.actionId, o.id)) as? CommandResult.Ok ?: continue
                val rate = eco.businessRate(r.state, id).net
                if (rate > bestRate) { best = r.state; bestRate = rate }
            }
            st = best
        }
        return st
    }

    /** Paid module actions that raise income immediately (e.g. a factory stage), ranked like any other investment. */
    fun moduleInvestments(s: GameState): List<Option> {
        val out = mutableListOf<Option>()
        for (b in s.businesses.values) {
            val def = engine.content.business(b.id)
            val m = def.mechanic as? MechanicDef.Module ?: continue
            val view = MechanicRegistry.of(m.kind).view(m, eco.moduleState(s, def, b, m), eco.mechCtx(s, def, b))
            for (a in view.controls.filterIsInstance<ViewControl.Action>()) {
                val cost = a.cost ?: continue
                if (!a.enabled || cost > s.cash) continue
                val cmd = Command.MechanicAction(b.id, a.actionId, a.value)
                gainOf(s, cmd, managedOnly = false)?.let { (_, g) -> if (g.isPositive) out += Option(cmd, cost, g, "${b.id}:${a.actionId}") }
            }
        }
        return out
    }

    /** Delayed-payoff module actions (trials, missions, surveys): an engaged player funds cheap ones now and then. */
    fun cheapDelayedActions(s: GameState, maxShareOfCash: Double): GameState {
        var st = s
        for (b in s.businesses.values.sortedBy { it.id }) {
            if (b.managerId != null) continue
            val def = engine.content.business(b.id)
            val m = def.mechanic as? MechanicDef.Module ?: continue
            val view = MechanicRegistry.of(m.kind).view(m, eco.moduleState(st, def, b, m), eco.mechCtx(st, def, b))
            val action = view.controls.filterIsInstance<ViewControl.Action>().firstOrNull { a ->
                a.enabled && a.actionId in setOf("fund", "launch", "survey", "greenlight", "roster", "redrill") &&
                    (a.cost == null || a.cost!! <= st.cash * Multiplier.ofDouble(maxShareOfCash))
            } ?: continue
            st = engine.tryApply(st, Command.MechanicAction(b.id, action.actionId, action.value))
        }
        return st
    }

    /** Forms any conglomerate whose requirements are met and whose fee is a modest share of cash. */
    fun formConglomerates(s: GameState, maxShareOfCash: Double): GameState {
        var st = s
        for (c in engine.content.conglomerates) {
            if (eco.conglomerateBlocker(st, c) == null && c.fee <= st.cash * Multiplier.ofDouble(maxShareOfCash)) {
                st = engine.tryApply(st, Command.FormConglomerate(c.id))
            }
        }
        if (st.conglomerates.size >= 3 && st.holdingName == null && engine.content.balance.holdingFee <= st.cash * Multiplier.ofDouble(maxShareOfCash)) {
            st = engine.tryApply(st, Command.FormHolding("Bot Holdings"))
        }
        return st
    }

    // ----- Markets -----

    private val market get() = engine.market.math

    /** Story missions that need a trade or a board seat. */
    fun marketMissions(s: GameState): GameState {
        var st = s
        val ch = engine.content.chapters.firstOrNull { it.number == st.progress.chapter } ?: return st
        for (mi in ch.missions) {
            if (mi.id in st.progress.completedMissions) continue
            when (val g = mi.goal) {
                is com.arnavgames.tycoon.core.content.GoalDef.MarketTrades -> if (st.market.trades < g.count && market.isUnlocked(st, "funds")) {
                    val amt = (st.cash * Multiplier.of("0.02")).max(Money.ofDollars(100))
                    if (amt <= st.cash) st = engine.tryApply(st, Command.BuyInstrument(FUND, amt))
                }
                is com.arnavgames.tycoon.core.content.GoalDef.BoardSeats -> if (market.boardSeats(st).size < g.count) {
                    val best = engine.content.markets.stocks.map { it.id to seatCost(st, it.id) }.minByOrNull { it.second }
                    if (best != null && best.second <= st.cash * Multiplier.of("0.6")) st = engine.tryApply(st, Command.BuyInstrument(best.first, best.second))
                }
                else -> Unit
            }
        }
        return st
    }

    /** What it costs to own 5.5 % of a company, including fee and slippage headroom. */
    fun seatCost(s: GameState, stockId: String): Money {
        val price = market.priceMicros(s, stockId) ?: return Money.ofCents(Long.MAX_VALUE)
        val target = market.outstanding(s, stockId).fraction(55, 1000).valueAt(price)
        val slip = market.slippage(s, stockId, target.toDollarsDouble())
        return target * Multiplier.ofDouble((1 + slip) * 1.02)
    }

    // ----- Real estate -----

    private val est get() = engine.estate

    private fun cheapestListing(s: GameState, maxCost: Money, type: String? = null): com.arnavgames.tycoon.core.estate.EstateEngine.Listing? =
        engine.content.estates.districts.filter { est.cityUnlocked(s, it.city) }
            .flatMap { est.listings(s, it.id) }
            .filter { type == null || it.type == type }
            .filter { est.buyCost(it, 0.0).first <= maxCost }
            .minByOrNull { it.price }

    /** Story missions that need properties, running businesses from owned property, tenant decisions, upkeep. */
    fun estates(s: GameState, renovateShare: String): GameState {
        if (!est.isUnlocked(s, "estates")) return s
        var st = s
        val ch = engine.content.chapters.firstOrNull { it.number == st.progress.chapter }
        ch?.missions?.forEach { mi ->
            val g = mi.goal
            if (mi.id !in st.progress.completedMissions && g is com.arnavgames.tycoon.core.content.GoalDef.PropertiesOwned) {
                repeat(g.count - st.estate.properties.size) {
                    val l = cheapestListing(st, st.cash * Multiplier.of("0.5")) ?: return@repeat
                    st = engine.tryApply(st, Command.BuyProperty(l.id))
                }
            }
        }
        // Run businesses from your own property when a suitable one is cheap (saves 10 % of their upkeep).
        val types = engine.content.estates.types
        for (b in st.businesses.keys.sorted()) {
            if (b in st.estate.hosted) continue
            val owned = st.estate.properties.values.firstOrNull { it.hostedBusiness == null && b in types.first { t -> t.id == it.type }.hosts }
            if (owned != null) { st = engine.tryApply(st, Command.HostBusiness(owned.id, b)); continue }
            val hostType = types.firstOrNull { b in it.hosts } ?: continue
            val l = cheapestListing(st, st.cash * Multiplier.of("0.05"), hostType.id) ?: continue
            val bought = engine.tryApply(st, Command.BuyProperty(l.id))
            if (bought !== st) st = engine.tryApply(bought, Command.HostBusiness(bought.estate.nextPropertyId - 1, b))
        }
        // Tenant decisions: take the cheapest option.
        for (ev in st.estate.events) {
            val def = engine.content.estates.eventById.getValue(ev.event)
            val best = def.choices.indices.minBy { def.choices[it].costRent - def.choices[it].gainRent }
            st = engine.tryApply(st, Command.ResolveTenantEvent(ev.id, best))
        }
        // Repairs and cheap renovations.
        for (p in st.estate.properties.values.sortedBy { it.id }) {
            if (est.busy(st, p.id)) continue
            val repair = p.condition < 700
            if (!repair && p.renovations >= engine.content.estates.config.maxRenovations) continue
            if (est.renovationCost(st, p, repair) <= st.cash * Multiplier.of(renovateShare)) st = engine.tryApply(st, Command.RenovateProperty(p.id, repair))
        }
        return st
    }

    // ----- Luxury -----

    /** Collection missions, and proxy bids on auctions (for the mission, or when a lot is cheap relative to cash). */
    fun luxury(s: GameState, opportunisticShare: String?): GameState {
        val lux = engine.luxury
        if (!lux.isUnlocked(s, "collection")) return s
        var st = s
        val ch = engine.content.chapters.firstOrNull { it.number == st.progress.chapter }
        var wantAuction = false
        ch?.missions?.forEach { mi ->
            if (mi.id in st.progress.completedMissions) return@forEach
            when (val g = mi.goal) {
                is com.arnavgames.tycoon.core.content.GoalDef.LuxuryOwned -> repeat(g.count - st.luxury.owned.size) {
                    val item = engine.content.luxury.items.filter { it.id !in st.luxury.owned && lux.categoryUnlocked(st, it.category) }
                        .minByOrNull { lux.buyCost(st, it) } ?: return@repeat
                    if (lux.buyCost(st, item) <= st.cash * Multiplier.of("0.3")) st = engine.tryApply(st, Command.BuyLuxury(item.id))
                }
                is com.arnavgames.tycoon.core.content.GoalDef.AuctionsWon -> wantAuction = true
                else -> Unit
            }
        }
        val a = st.luxury.auction
        if (a != null && a.leader != com.arnavgames.tycoon.core.luxury.LuxuryEngine.PLAYER && lux.isUnlocked(st, "auctions")) {
            val estimate = lux.marketPrice(engine.content.luxury.itemById.getValue(a.item), st.simTimeMs)
            // For the story mission, bid above any rival's ceiling (up to 1.84× estimate); otherwise stay disciplined.
            val max = estimate * Multiplier.of(if (wantAuction) "2.0" else "1.4")
            val affordable = max <= st.cash * Multiplier.of(if (wantAuction) "0.6" else (opportunisticShare ?: "0"))
            if ((wantAuction || opportunisticShare != null) && affordable && max > a.playerMax) st = engine.tryApply(st, Command.SetMaxBid(max))
        }
        return st
    }

    // ----- Desk and finance (Phase 9) -----

    /** Expected value of a card option in hours of total income (a bot's rough judgement). */
    fun optionValue(s: GameState, card: com.arnavgames.tycoon.core.state.CardState, o: com.arnavgames.tycoon.core.content.CardOptionDef): Double {
        val total = eco.totalNetRate(s).perSecond().toDollarsDouble().coerceAtLeast(1e-9)
        val share = card.business?.let { eco.businessRate(s, it).net.perSecond().toDollarsDouble() / total } ?: 0.0
        val hourly = total * 3600
        return o.outcomes.sumOf { oc ->
            oc.p * oc.effects.sumOf { e ->
                when (e.type) {
                    "cash" -> e.hours
                    "donation", "rd" -> -e.hours * 0.9
                    "income" -> e.pct * e.hours * (if (e.target == "all") 1.0 else share)
                    "credit" -> e.points * 0.01
                    // Selling means rebuying later: count the rebuild cost and a day of lost income.
                    "sell" -> card.business?.let { b ->
                        val invested = eco.investedIn(engine.content.business(b), s.businesses[b]?.level ?: 1).toDollarsDouble()
                        (engine.desk.valuation(s, b).toDollarsDouble() * e.multiple - invested) / hourly - 24 * share
                    } ?: 0.0
                    else -> 0.0
                }
            }
        }
    }

    /** Resolves pending decision cards: "best" (expected value), "safe" (cheapest worst case) or "random". */
    fun desk(s0: GameState, style: String, rng: Pcg32? = null): GameState {
        var s = s0
        for (card in s0.world.cards.sortedBy { it.id }) {
            val def = engine.content.world.cardById[card.card] ?: continue
            val usable = def.options.indices.filter { i ->
                val o = def.options[i]
                engine.desk.optionAvailable(s, card, o) && engine.desk.maxCost(s, o) <= s.cash
            }
            if (usable.isEmpty()) continue
            val pick = when (style) {
                "random" -> usable[rng!!.nextInt(usable.size)]
                "safe" -> usable.minWith(compareBy<Int> { engine.desk.maxCost(s, def.options[it]) }.thenByDescending { optionValue(s, card, def.options[it]) })
                else -> usable.maxBy { optionValue(s, card, def.options[it]) }
            }
            val before = s.cash
            s = engine.tryApply(s, Command.ResolveCard(card.id, pick))
            if (System.getenv("ABT_TRACE_CARDS") != null) {
                val hourly = engine.economy.totalNetRate(s0).over(3600)
                println("[card] ${card.card} opt=$pick cash ${com.arnavgames.tycoon.core.money.MoneyFormat.short(s.cash - before)} hourly=${com.arnavgames.tycoon.core.money.MoneyFormat.short(hourly)}")
            }
        }
        return s
    }

    /** Taxes and the never-stuck guarantees. filing: "legit", "aggressive" or null (let the window auto-file). */
    fun finance(s0: GameState, filing: String?): GameState {
        var s = s0
        if (s.finance.tax.pending != null && filing != null) s = engine.tryApply(s, Command.FileTaxes(aggressive = filing == "aggressive"))
        if (engine.credit.canRestructure(s)) s = engine.tryApply(s, Command.Restructure)
        if (engine.credit.phoenixAvailable(s)) s = engine.tryApply(s, Command.AcceptPhoenix)
        return s
    }

    /** Parks spare cash in the total-market fund when a session ends. Positions that hold board seats are never touched. */
    fun parkCash(s: GameState, incomePerSec: Money, share: String = "0.9"): GameState {
        if (!market.isUnlocked(s, "funds")) return s
        if (s.cash < (incomePerSec * 300).max(Money.ofDollars(1_000))) return s
        return engine.tryApply(s, Command.BuyInstrument(FUND, s.cash * Multiplier.of(share)))
    }

    /** Sells the parked fund at the start of a session so the money can go back into businesses. */
    fun unpark(s: GameState): GameState = if (FUND in s.market.holdings) engine.tryApply(s, Command.SellInstrument(FUND, 1000)) else s

    fun claimAll(s: GameState): GameState {
        var st = s.progress.claimableMissions.sorted().fold(s) { acc, id -> engine.tryApply(acc, Command.ClaimMission(id)) }
        // Phase 10: the daily reward, finished daily/weekly missions, achievements and season tiers.
        val prog = engine.progression
        if (prog.ladderAvailable(st)) st = engine.tryApply(st, Command.ClaimDailyReward)
        st.meta.daily.forEachIndexed { i, m -> if (!m.claimed && prog.done(st, m)) st = engine.tryApply(st, Command.ClaimDaily(i)) }
        st.meta.weekly.forEachIndexed { i, m -> if (!m.claimed && prog.done(st, m)) st = engine.tryApply(st, Command.ClaimWeekly(i)) }
        st.meta.achievementsClaimable.sorted().forEach { st = engine.tryApply(st, Command.ClaimAchievement(it)) }
        engine.content.progression.seasonTrack.filter { it.xp <= st.meta.seasonXp && it.tier !in st.meta.seasonClaimedFree }
            .forEach { st = engine.tryApply(st, Command.ClaimSeasonTier(it.tier)) }
        return st
    }

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

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean): GameState {
        val p = Planner(engine)
        var s = p.claimAll(state)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        s = engine.tryApply(s, Command.CollectAll)
        if (sessionStart) s = p.unpark(engine.tryApply(s, Command.ActivateFrenzy))
        s = p.marketMissions(s)
        s = p.estates(s, "0.02")
        s = p.luxury(s, "0.01")
        s = p.desk(s, "best")
        s = p.finance(s, if (name == "risky_trader") "aggressive" else "legit")
        s = p.tuneMechanics(s, skill = 1.0)
        val income = engine.economy.totalNetRate(s).perSecond()
        // Managers keep money flowing offline; an engaged player hires one as soon as it pays back within ~20 minutes.
        p.firstManager(s)?.let { (cmd, cost) -> if (cost <= income * 1200 || cost <= s.cash * Multiplier.of("0.5")) s = engine.tryApply(s, cmd) }
        s = p.hqIfUseful(s, slotsFull = engine.economy.usedSlots(s) >= engine.economy.slots(s), incomePerSec = income, horizonSec = 600)
        // Saving for the business the story asks for (within a day of income): open it as soon as it's affordable.
        val story = p.storyTarget(s, income, 24 * 3600L)
        s = p.openNewBusinesses(s, if (story != null && story <= s.cash) 1.0 else 0.7)
        val saving = p.storyTarget(s, income, 24 * 3600L) ?: Money.ZERO
        s = p.formConglomerates(s, 0.5)
        if (sessionStart) s = p.cheapDelayedActions(s, 0.05)
        // Greedy: keep buying the fastest payback until money runs out (respecting any reserve).
        repeat(400) {
            val reserve = maxOf(s.cash * Multiplier.ofDouble(reserveFraction), saving)
            val best = (p.options(s, managedOnly = false) + p.moduleInvestments(s)).filter { s.cash - it.cost >= reserve }.minByOrNull { it.paybackSeconds } ?: return@repeat
            // Normally only fast paybacks; but an engaged player sitting on a pile of cash puts it to work.
            if (best.paybackSeconds > 12 * 3600 && best.cost > s.cash * Multiplier.of("0.25")) return@repeat
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
        if (sessionEnd) s = p.parkCash(s, engine.economy.totalNetRate(s).perSecond(), if (reserveFraction > 0) "0.6" else "0.9")
        return s
    }
}

/** Checks in briefly twice a day: collects, hires managers, buys the obvious upgrades. Never taps after day one. */
class IdleBot : Bot {
    override val name = "idle"
    override val schedule = PlaySchedule(sessionStarts = listOf(9 * 60, 21 * 60), sessionMinutes = 3, firstSessionMinutes = 10, tapsPerSecond = 2.0, tapMinutesPerSession = 2, day0ExtraSessions = listOf(60, 240))

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean): GameState {
        val p = Planner(engine)
        var s = p.claimAll(state)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        s = engine.tryApply(s, Command.CollectAll)
        if (sessionStart) s = p.unpark(s)
        s = p.marketMissions(s)
        s = p.estates(s, "0.0")
        s = p.luxury(s, null)
        s = p.desk(s, "safe")
        s = p.finance(s, null)
        p.firstManager(s)?.let { (cmd, _) -> s = engine.tryApply(s, cmd) }
        // Idle players use the free auto-plan button rather than fine-tuning.
        for (b in s.businesses.values) if (engine.content.business(b.id).mechanic is MechanicDef.Logistics && b.managerId == null) s = engine.tryApply(s, Command.AutoPlanRoutes(b.id))
        val income = engine.economy.totalNetRate(s, managedOnly = true).perSecond()
        s = p.hqIfUseful(s, slotsFull = engine.economy.usedSlots(s) >= engine.economy.slots(s), incomePerSec = income, horizonSec = 1800)
        // Idle players follow the Next Move card too: save for the business it asks for (within two days of income).
        val story = p.storyTarget(s, income, 48 * 3600L)
        s = p.openNewBusinesses(s, if (story != null && story <= s.cash) 1.0 else 0.7)
        val saving = p.storyTarget(s, income, 48 * 3600L) ?: Money.ZERO
        s = p.formConglomerates(s, 0.5)
        s = p.missionLevels(s)
        repeat(400) {
            // Idle players buy the obvious best-value upgrade (they hire managers above, so most income keeps flowing).
            val best = p.options(s, managedOnly = false).filter { s.cash - it.cost >= saving }.minByOrNull { it.paybackSeconds } ?: return@repeat
            if (best.paybackSeconds > 12 * 3600) return@repeat
            val ns = engine.tryApply(s, best.cmd)
            if (ns === s) return@repeat
            s = ns
        }
        p.managerOption(s)?.let { (cmd, cost) -> if (cost <= income * 600) s = engine.tryApply(s, cmd) }
        if (sessionEnd) s = p.parkCash(s, income)
        return s
    }
}

/** Makes poor choices: random affordable purchases, never tunes mechanics, overspends on managers. */
class PoorChoicesBot : Bot {
    override val name = "poor_choices"
    override val schedule = PlaySchedule(sessionStarts = listOf(10 * 60, 18 * 60), sessionMinutes = 8, firstSessionMinutes = 12, tapsPerSecond = 1.5, tapMinutesPerSession = 2, day0ExtraSessions = listOf(90, 300))

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean): GameState {
        var s = state
        if (rng.chance(0.5)) s = Planner(engine).claimAll(s)
        if (rng.chance(0.3)) s = Planner(engine).marketMissions(s)
        if (s.businesses.isEmpty()) s = engine.tryApply(s, Command.OpenBusiness("retail"))
        if (rng.chance(0.6)) s = engine.tryApply(s, Command.CollectAll)
        val p = Planner(engine)
        if (rng.chance(0.7)) s = p.desk(s, "random", rng)
        // Poor choices with money: borrows the maximum, files aggressively, but still takes the never-stuck offers.
        if (rng.chance(0.3)) engine.credit.borrowLimit(s).let { lim -> if (lim.isPositive) s = engine.tryApply(s, Command.TakeLoan(lim, 7)) }
        s = p.finance(s, "aggressive")
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

/**
 * Plays the businesses like the active bot, then speculates: at the end of each session it puts 30 % of its cash into
 * stocks with fresh good news (and Report/Confirmed earnings hints) and into hyped coins; it sells everything
 * speculative at the start of the next session. Measures whether markets dominate and how bad the downside is.
 */
class RiskyTraderBot : Bot {
    private val base = ActiveBot(name = "risky_trader")
    override val name = "risky_trader"
    override val schedule = base.schedule

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean): GameState {
        val p = Planner(engine)
        val math = engine.market.math
        var s = state
        if (sessionStart) {
            val seats = math.boardSeats(s).toSet()
            for (id in s.market.holdings.keys.sorted()) if (id !in seats && id != FUND) s = engine.tryApply(s, Command.SellInstrument(id, 1000))
            for (id in s.market.shorts.keys.sorted()) s = engine.tryApply(s, Command.CoverShort(id))
        }
        s = base.act(engine, s, rng, sessionStart, sessionEnd = false)
        if (!sessionEnd || !math.isUnlocked(s, "stocks")) return s
        // Borrows to the limit for three days to speculate (GDD bots: "borrows to the limit").
        engine.credit.borrowLimit(s).let { lim -> if (lim.isPositive) s = engine.tryApply(s, Command.TakeLoan(lim, 3)) }
        val budget = s.cash * Multiplier.of("0.3")
        if (budget < Money.ofDollars(1_000)) return s
        val since = s.market.tick - 12 * 60
        val good = s.market.news.filter { it.tick >= since && it.kind == "company" && it.effect > 0 }.map { it.target } +
            s.market.hints.filter { it.magnitude > 0 && it.reliability != "Rumor" }.map { it.stockId }
        val bad = s.market.news.filter { it.tick >= since && it.kind == "company" && it.effect < -0.05 }.map { it.target }
        val coins = if (math.isUnlocked(s, "crypto")) s.market.quotes.filter { (id, q) -> id.startsWith("c_") && q.hypeUntil > s.market.tick }.keys.sorted() else emptyList()
        val picks = (good.distinct().take(3) + coins.take(1)).ifEmpty { listOf("fund_growth") }
        val each = budget / picks.size.toLong()
        for (id in picks) s = engine.tryApply(s, Command.BuyInstrument(id, each))
        if (math.isUnlocked(s, "shorts")) bad.distinct().take(1).forEach { id -> s = engine.tryApply(s, Command.OpenShort(id, budget / 4L)) }
        return s
    }
}

/**
 * The active policy plus every demo purchase as soon as it is available (docs/ECONOMY.md §10.1): the starter pack,
 * permanent boosts, extra slots and offline time, VIP, the Season Pass and a 24-hour ×2 boost whenever none is running.
 * Measures the spender gap (target: 25–40 % faster than active, never more than 2×).
 */
class HeavySpenderBot : Bot {
    private val base = ActiveBot(name = "heavy_spender")
    override val name = "heavy_spender"
    override val schedule = base.schedule

    override fun act(engine: GameEngine, state: GameState, rng: Pcg32, sessionStart: Boolean, sessionEnd: Boolean): GameState {
        var s = state
        if (sessionStart) {
            fun demo(id: String) { s = engine.tryApply(s, Command.DemoPurchase(id)) }
            fun bullion(id: String) {
                val price = engine.content.store.productById[id]?.bullionPrice ?: return
                if (s.ledger.bullion.cents.toLong() < price) demo("bullion_2600")
                s = engine.tryApply(s, Command.BuyWithBullion(id))
            }
            demo("starter_pack")
            repeat(3) { demo("permanent_5") }
            repeat(3) { bullion("slot_1") }
            repeat(2) { bullion("offline_2h") }
            if (!engine.economy.store.vipActive(s)) demo("vip_30")
            if (!engine.economy.store.seasonPassActive(s, engine.dynasty.season(s.simTimeMs))) demo("season_pass")
            if (s.world.modifiers.none { it.source == com.arnavgames.tycoon.core.store.StoreEngine.BOOST && it.untilSim > s.simTimeMs }) bullion("boost_24h")
        }
        return base.act(engine, s, rng, sessionStart, sessionEnd)
    }
}

/** Plays like the active bot but keeps a 30% cash buffer and avoids promotions. */
fun cautiousBot() = ActiveBot(name = "cautious", reserveFraction = 0.30, promos = false)

internal const val FUND = "fund_total"

/** Timers are serviced by the simulation loop; exposed for reports. */
internal fun GameState.expansionRunning() = timers.any { it.kind == TimerKind.Expansion }
