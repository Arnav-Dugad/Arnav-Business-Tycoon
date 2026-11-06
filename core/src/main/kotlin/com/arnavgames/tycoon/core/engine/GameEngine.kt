package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.LedgerState
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Accrual
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.BusinessState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import com.arnavgames.tycoon.core.state.TimerKind
import com.arnavgames.tycoon.core.state.TimerState
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.core.time.OfflineCalculator

/**
 * The pure rules engine (docs/ARCHITECTURE.md §3.1).
 *
 * [advance] is event-driven: it jumps from boundary to boundary (minute ticks, timers, frenzy/promo ends,
 * storage filling up). Between boundaries every rate is constant and is integrated exactly, so
 * `advance(a + b) == advance(a).then(advance(b))` — live play, offline catch-up and the simulator share one code path.
 */
class GameEngine(val content: ContentPack) {
    val economy = Economy(content)
    private val missions = Missions(content, economy)
    val market = com.arnavgames.tycoon.core.market.MarketEngine(content.markets)
    val trading = com.arnavgames.tycoon.core.market.Trading(market)

    fun newGame(worldSeed: Long, contentHash: String, clock: GameClock): GameState {
        val base = GameState(
            worldSeed = worldSeed,
            contentHash = contentHash,
            createdAtWall = clock.wallMillis(),
            clock = OfflineCalculator.initial(clock),
        )
        val start = content.balance.startingCash
        val ledger = if (start.isPositive) {
            (Ledger.post(base.ledger, 0, TxnCategory.Reward, "starting capital", listOf(Posting(Accounts.CASH, start), Posting(Accounts.WORLD, -start))) as Ledger.Result.Ok).state
        } else base.ledger
        // The market opens with the world, so every screen has quotes from the first second.
        return market.tick(base.copy(ledger = ledger), 0, mutableListOf())
    }

    // =====================================================================================
    // Time
    // =====================================================================================

    fun advance(state: GameState, dtMs: Long): AdvanceResult {
        require(dtMs >= 0) { "cannot advance by negative time" }
        var s = state
        val events = mutableListOf<GameEvent>()
        var earned = Money.ZERO
        val end = s.simTimeMs + dtMs
        while (s.simTimeMs < end) {
            val now = s.simTimeMs
            val stop = nextBoundary(s, now, end)
            val seg = integrate(s, stop - now, events)
            s = seg.first.copy(simTimeMs = stop)
            earned += seg.second
            s = onBoundary(s, stop, events)
        }
        return AdvanceResult(s, events, earned)
    }

    private fun nextBoundary(s: GameState, now: Long, end: Long): Long {
        var stop = minOf(end, (now / MINUTE + 1) * MINUTE)
        for (t in s.timers) if (t.endSim in (now + 1)..<stop) stop = t.endSim
        if (s.hustle.frenzyUntil in (now + 1)..<stop) stop = s.hustle.frenzyUntil
        for (b in s.businesses.values) {
            val m = b.mechanic
            if (m is MechanicState.Retail && m.promoUntil in (now + 1)..<stop) stop = m.promoUntil
            if (b.managerId == null && !b.mothballed) {
                val cap = economy.storageCap(s, b.id, now)
                if (b.stored < cap) {
                    val ms = b.accrual.millisUntil(economy.businessRate(s, b.id, now).net, cap - b.stored)
                    if (ms != null && ms > 0 && now + ms < stop) stop = now + ms
                }
            }
        }
        return stop
    }

    /** Integrates all income over [dt] ms with constant rates. Returns the new state and managed cash earned. */
    private fun integrate(s: GameState, dt: Long, events: MutableList<GameEvent>): Pair<GameState, Money> {
        if (dt <= 0) return s to Money.ZERO
        val now = s.simTimeMs
        var ledger = s.ledger
        var earned = Money.ZERO
        val newBusinesses = s.businesses.toMutableMap()
        for (id in s.businesses.keys.sorted()) {
            val b = s.businesses.getValue(id)
            if (b.mothballed) continue
            val rate = economy.businessRate(s, id, now).net
            if (b.managerId != null) {
                val (amount, acc) = b.accrual.accrue(rate, dt)
                // Stamp at segment start: segments never cross a minute boundary, so this is always the right minute.
                ledger = Ledger.earn(ledger, now, id, amount)
                earned += amount
                newBusinesses[id] = b.copy(accrual = acc)
            } else {
                val cap = economy.storageCap(s, id, now)
                if (b.stored >= cap) continue
                val (amount, acc) = b.accrual.accrue(rate, dt)
                val total = b.stored + amount
                newBusinesses[id] = if (total >= cap) {
                    events += GameEvent.StorageFull(id)
                    b.copy(stored = cap, accrual = Accrual())
                } else {
                    b.copy(stored = total, accrual = acc)
                }
            }
        }
        val stats = if (earned.isPositive) s.stats.copy(lifetimeEarned = s.stats.lifetimeEarned + earned) else s.stats
        return s.copy(ledger = ledger, businesses = newBusinesses, stats = stats) to earned
    }

    private fun onBoundary(state: GameState, now: Long, events: MutableList<GameEvent>): GameState {
        var s = state
        // Timers
        val due = s.timers.filter { it.endSim <= now }.sortedWith(compareBy({ it.endSim }, { it.id }))
        if (due.isNotEmpty()) {
            s = s.copy(timers = s.timers - due.toSet())
            for (t in due) s = completeTimer(s, t, events)
        }
        if (s.hustle.frenzyUntil == now) s = s.copy(hustle = s.hustle.copy(combo = 0))
        for (b in s.businesses.values) {
            val m = b.mechanic
            if (m is MechanicState.Retail && m.promoUntil == now) events += GameEvent.PromoEnded(b.id)
        }
        // Minute tick: reputation drift, managers act, missions and peak net worth update.
        if (now % MINUTE == 0L) {
            s = market.tick(s, now / MINUTE, events)
            s = minuteTick(s)
            s = missions.evaluate(s, events)
            val nw = economy.netWorth(s)
            if (nw > s.stats.peakNetWorth) s = s.copy(stats = s.stats.copy(peakNetWorth = nw))
        }
        return s
    }

    private fun minuteTick(state: GameState): GameState {
        var s = state
        for (id in state.businesses.keys.sorted()) {
            val b = s.businesses.getValue(id)
            val def = content.business(id)
            var nb = b
            when (val m = def.mechanic) {
                is MechanicDef.Restaurant -> {
                    val st = nb.mechanic as? MechanicState.Restaurant ?: Mechanics.restaurantInitial(m)
                    nb = nb.copy(mechanic = Mechanics.restaurantMinute(m, st))
                }
                is MechanicDef.Module -> {
                    val module = MechanicRegistry.of(m.kind)
                    val ctx = economy.mechCtx(s, def, nb)
                    var ms = module.minute(m, economy.moduleState(s, def, nb, m), ctx)
                    if (nb.managerId != null && !nb.mothballed) {
                        // Managers run the mechanic and may spend a small budget on upkeep-style decisions.
                        val skill = economy.managerSkill(def, nb) ?: 0.0
                        val policy = def.managers.first { it.id == nb.managerId }.policy
                        val budget = (s.cash * Multiplier.ofDouble(content.balance.managerBudgetCashShare))
                            .min(ctx.incomeSecond * content.balance.managerBudgetIncomeSeconds.toLong())
                        val r = module.manage(m, ms, ctx, skill, policy, budget.max(Money.ZERO))
                        if (r.cost.isPositive && r.cost <= budget) {
                            val paid = Ledger.spend(s.ledger, s.simTimeMs, TxnCategory.Purchase, "${def.name}: manager decision", "manager_ops", r.cost)
                            if (paid is Ledger.Result.Ok) { s = s.copy(ledger = paid.state); ms = r.state }
                        } else if (!r.cost.isPositive) {
                            ms = r.state
                        }
                    }
                    nb = nb.copy(mechanic = ms)
                }
                else -> Unit
            }
            if (nb.managerId != null && def.mechanic !is MechanicDef.Module) nb = managerAct(nb)
            if (nb != b) s = s.copy(businesses = s.businesses + (id to nb))
        }
        return s
    }

    /** Managers run the business's mechanic at their skill level (active players can beat them). */
    private fun managerAct(b: BusinessState): BusinessState {
        val def = content.business(b.id)
        val skill = economy.managerSkill(def, b) ?: return b
        val policy = def.managers.first { it.id == b.managerId }.policy
        return when (val m = def.mechanic) {
            is MechanicDef.Retail -> {
                val st = b.mechanic as? MechanicState.Retail ?: MechanicState.Retail()
                b.copy(mechanic = st.copy(pricePct = Mechanics.retailManagerPrice(m, skill)))
            }
            is MechanicDef.Restaurant -> {
                val st = b.mechanic as? MechanicState.Restaurant ?: Mechanics.restaurantInitial(m)
                val tiers = m.qualityTiers
                val choice = when (policy) {
                    com.arnavgames.tycoon.core.content.ManagerPolicy.Safe -> tiers[tiers.size / 2].id
                    com.arnavgames.tycoon.core.content.ManagerPolicy.Aggressive -> tiers.last().id
                    com.arnavgames.tycoon.core.content.ManagerPolicy.Balanced ->
                        tiers.maxBy { Mechanics.restaurantTierValue(m, it.id, def.upkeep.toDouble()) }.id
                }
                if (st.qualityTier == choice) b else b.copy(mechanic = st.copy(qualityTier = choice))
            }
            is MechanicDef.Logistics -> {
                val plan = Mechanics.logisticsPlan(m, b.level, skill)
                val st = b.mechanic as? MechanicState.Logistics ?: MechanicState.Logistics()
                if (st.assignments == plan) b else b.copy(mechanic = MechanicState.Logistics(plan))
            }
            MechanicDef.Simple, is MechanicDef.Module -> b
        }
    }

    private fun completeTimer(s: GameState, t: TimerState, events: MutableList<GameEvent>): GameState = when (t.kind) {
        TimerKind.Expansion -> {
            val b = s.businesses[t.targetId]
            if (b == null) s else {
                events += GameEvent.ExpansionCompleted(t.targetId, t.detailId)
                s.copy(businesses = s.businesses + (b.id to b.copy(expansionsDone = b.expansionsDone + t.detailId)))
            }
        }
    }

    // =====================================================================================
    // Commands
    // =====================================================================================

    fun apply(state: GameState, cmd: Command): CommandResult {
        val events = mutableListOf<GameEvent>()
        val result: Either = when (cmd) {
            is Command.OpenBusiness -> openBusiness(state, cmd.businessId, events)
            is Command.BuyLevels -> buyLevels(state, cmd.businessId, cmd.count, events)
            is Command.Collect -> collect(state, cmd.businessId, events)
            Command.CollectAll -> Either.Ok(state.businesses.keys.sorted().fold(state) { acc, id ->
                (collect(acc, id, events) as? Either.Ok)?.state ?: acc
            })
            is Command.HireManager -> hireManager(state, cmd.businessId, cmd.managerId, events)
            is Command.StartExpansion -> startExpansion(state, cmd.businessId, cmd.expansionId, events)
            is Command.RushTimer -> rushTimer(state, cmd.timerId, events)
            is Command.HustleTap -> hustle(state, cmd.taps, events)
            Command.ActivateFrenzy -> frenzy(state, events)
            Command.UpgradeHq -> upgradeHq(state, events)
            is Command.SetMothballed -> mothball(state, cmd.businessId, cmd.mothballed)
            is Command.ClaimMission -> missions.claim(state, cmd.missionId, events)
            is Command.AdvanceOnboarding -> Either.Ok(state.copy(progress = state.progress.copy(onboardingStep = maxOf(state.progress.onboardingStep, cmd.toStep))))
            is Command.SetRetailPrice -> setRetailPrice(state, cmd.businessId, cmd.pricePct)
            is Command.StartRetailPromo -> startPromo(state, cmd.businessId)
            is Command.SetRestaurantQuality -> setQuality(state, cmd.businessId, cmd.tierId)
            is Command.AssignVehicles -> assignVehicles(state, cmd.businessId, cmd.routeId, cmd.count)
            is Command.AutoPlanRoutes -> autoPlan(state, cmd.businessId)
            is Command.MechanicAction -> mechanicAction(state, cmd.businessId, cmd.action, cmd.value)
            is Command.FormConglomerate -> formConglomerate(state, cmd.conglomerateId, events)
            is Command.FormHolding -> formHolding(state, cmd.name, events)
            is Command.BuyInstrument -> trade(trading.buy(state, cmd.instrumentId, cmd.amount, events))
            is Command.SellInstrument -> trade(trading.sell(state, cmd.instrumentId, cmd.permille, events))
            is Command.OpenShort -> trade(trading.openShort(state, cmd.stockId, cmd.amount, events))
            is Command.CoverShort -> trade(trading.cover(state, cmd.stockId, forced = false, events = events))
        }
        return when (result) {
            is Either.Err -> CommandResult.Rejected(result.error)
            // Peak net worth is tracked on minute ticks (cheap enough there, and commands stay fast).
            is Either.Ok -> CommandResult.Ok(missions.evaluate(result.state, events), events)
        }
    }

    internal sealed interface Either {
        data class Ok(val state: GameState) : Either
        data class Err(val error: GameError) : Either
    }

    private fun trade(r: com.arnavgames.tycoon.core.market.Trading.Result): Either = when (r) {
        is com.arnavgames.tycoon.core.market.Trading.Result.Ok -> Either.Ok(r.state)
        is com.arnavgames.tycoon.core.market.Trading.Result.Err -> Either.Err(r.error)
    }

    private fun spend(s: GameState, amount: Money, category: TxnCategory, memo: String, kind: String): Either {
        return when (val r = Ledger.spend(s.ledger, s.simTimeMs, category, memo, kind, amount)) {
            is Ledger.Result.Ok -> Either.Ok(s.copy(ledger = r.state))
            is Ledger.Result.Err -> when (val e = r.error) {
                is LedgerError.InsufficientFunds -> Either.Err(GameError.InsufficientFunds(e.needed, e.available))
            }
        }
    }

    /** Progress toward a mission goal in [0, 1] (for UI bars). */
    fun goalProgress(state: GameState, goal: com.arnavgames.tycoon.core.content.GoalDef): Double = missions.progress(state, goal)

    fun isUnlocked(state: GameState, businessId: String): Boolean {
        val def = content.businessById[businessId] ?: return false
        return def.chapter <= state.progress.chapter
    }

    private fun openBusiness(s: GameState, id: String, events: MutableList<GameEvent>): Either {
        val def = content.businessById[id] ?: return Either.Err(GameError.NotFound("business $id"))
        if (id in s.businesses) return Either.Err(GameError.AlreadyDone)
        if (!isUnlocked(s, id)) return Either.Err(GameError.Locked("Unlocks in chapter ${def.chapter}"))
        if (economy.usedSlots(s) >= economy.slots(s)) return Either.Err(GameError.NoFreeSlot)
        val cost = economy.openCost(s, def)
        val paid = if (cost.isPositive) spend(s, cost, TxnCategory.Purchase, "open ${def.name}", "business_open") else Either.Ok(s)
        if (paid is Either.Err) return paid
        val ps = (paid as Either.Ok).state
        val mech: MechanicState = when (val m = def.mechanic) {
            is MechanicDef.Retail -> MechanicState.Retail()
            is MechanicDef.Restaurant -> Mechanics.restaurantInitial(m)
            is MechanicDef.Logistics -> MechanicState.Logistics(mapOf(m.routes.first().id to Mechanics.vehicles(m, 1)))
            is MechanicDef.Module -> MechanicRegistry.of(m.kind).initial(m, economy.mechCtx(ps, def, BusinessState(id = id)))
            MechanicDef.Simple -> MechanicState.None
        }
        events += GameEvent.BusinessOpened(id)
        return Either.Ok(ps.copy(businesses = ps.businesses + (id to BusinessState(id = id, mechanic = mech, openedAtSim = ps.simTimeMs))))
    }

    private fun buyLevels(s: GameState, id: String, count: Int, events: MutableList<GameEvent>): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        val def = content.business(id)
        val n = if (count < 0) economy.maxAffordable(def, b.level, s.cash) else count
        if (n <= 0) return Either.Err(GameError.InsufficientFunds(economy.levelCost(def, b.level, 1), s.cash))
        val cost = economy.levelCost(def, b.level, n)
        val paid = spend(s, cost, TxnCategory.Upgrade, "${def.name} +$n", "business_levels")
        if (paid is Either.Err) return paid
        val ps = (paid as Either.Ok).state
        val newLevel = b.level + n
        var nb = b.copy(level = newLevel)
        // New vehicles go to the depot's default route so they are never idle.
        val m = def.mechanic
        if (m is MechanicDef.Logistics) {
            val st = nb.mechanic as? MechanicState.Logistics ?: MechanicState.Logistics()
            val extra = Mechanics.vehicles(m, newLevel) - Mechanics.assignedTotal(st)
            if (extra > 0) {
                val first = m.routes.first().id
                nb = nb.copy(mechanic = st.copy(assignments = st.assignments + (first to ((st.assignments[first] ?: 0) + extra))))
            }
        }
        if (nb.managerId != null) nb = managerAct(nb)
        events += GameEvent.LevelsBought(id, n, newLevel, cost)
        def.tiers.filter { it.level in (b.level + 1)..newLevel }.forEach { events += GameEvent.TierReached(id, it.name, it.level) }
        return Either.Ok(ps.copy(businesses = ps.businesses + (id to nb), stats = ps.stats.copy(levelsBought = ps.stats.levelsBought + n)))
    }

    private fun collect(s: GameState, id: String, events: MutableList<GameEvent>): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        if (!b.stored.isPositive) return Either.Ok(s)
        val ledger = Ledger.earn(s.ledger, s.simTimeMs, id, b.stored)
        events += GameEvent.Collected(id, b.stored)
        return Either.Ok(
            s.copy(
                ledger = ledger,
                businesses = s.businesses + (id to b.copy(stored = Money.ZERO)),
                stats = s.stats.copy(lifetimeEarned = s.stats.lifetimeEarned + b.stored),
            ),
        )
    }

    private fun hireManager(s: GameState, id: String, managerId: String, events: MutableList<GameEvent>): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        val def = content.business(id)
        val m = def.managers.firstOrNull { it.id == managerId } ?: return Either.Err(GameError.NotFound("manager $managerId"))
        if (b.managerId == managerId) return Either.Err(GameError.AlreadyDone)
        val paid = spend(s, m.cost, TxnCategory.Hire, "hire ${m.name}", "managers")
        if (paid is Either.Err) return paid
        var ps = (paid as Either.Ok).state
        // Hiring auto-collects anything waiting, then the manager takes over the mechanic immediately.
        ps = (collect(ps, id, events) as Either.Ok).state
        val nb = managerAct(ps.businesses.getValue(id).copy(managerId = managerId, accrual = Accrual()))
        events += GameEvent.ManagerHired(id, managerId)
        return Either.Ok(ps.copy(businesses = ps.businesses + (id to nb)))
    }

    private fun startExpansion(s: GameState, id: String, expansionId: String, events: MutableList<GameEvent>): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        val def = content.business(id)
        val e = def.expansions.firstOrNull { it.id == expansionId } ?: return Either.Err(GameError.NotFound("expansion $expansionId"))
        if (expansionId in b.expansionsDone || s.timers.any { it.targetId == id && it.detailId == expansionId }) return Either.Err(GameError.AlreadyDone)
        if (s.timers.any { it.kind == TimerKind.Expansion && it.targetId == id }) return Either.Err(GameError.Invalid("Another expansion is already under way"))
        if (b.level < e.requiresLevel) return Either.Err(GameError.Locked("Requires level ${e.requiresLevel}"))
        val paid = spend(s, e.cost, TxnCategory.Expansion, "${def.name}: ${e.name}", "expansions")
        if (paid is Either.Err) return paid
        val ps = (paid as Either.Ok).state
        val end = ps.simTimeMs + e.durationSeconds * 1000
        val timer = TimerState(ps.nextTimerId, TimerKind.Expansion, id, expansionId, ps.simTimeMs, end)
        events += GameEvent.ExpansionStarted(id, expansionId, end)
        return Either.Ok(ps.copy(timers = ps.timers + timer, nextTimerId = ps.nextTimerId + 1))
    }

    fun rushCostFavor(s: GameState, t: TimerState): Long {
        val remainingMin = ((t.endSim - s.simTimeMs).coerceAtLeast(0) + MINUTE - 1) / MINUTE
        return kotlin.math.ceil(remainingMin * content.balance.favorPerRushMinute).toLong().coerceAtLeast(1)
    }

    private fun rushTimer(s: GameState, timerId: Long, events: MutableList<GameEvent>): Either {
        val t = s.timers.firstOrNull { it.id == timerId } ?: return Either.Err(GameError.NotFound("timer $timerId"))
        val cost = rushCostFavor(s, t)
        val have = s.ledger.favor.centsOrNull ?: Long.MAX_VALUE
        if (have < cost) return Either.Err(GameError.InsufficientFavor(cost, have))
        val r = Ledger.post(
            s.ledger, s.simTimeMs, TxnCategory.PremiumSpend, "rush timer",
            listOf(Posting(Accounts.FAVOR, Money.ofCents(-cost)), Posting(Accounts.WORLD_PREMIUM, Money.ofCents(cost))),
        ) as? Ledger.Result.Ok ?: return Either.Err(GameError.InsufficientFavor(cost, have))
        val s1 = s.copy(ledger = r.state, timers = s.timers - t)
        return Either.Ok(completeTimer(s1, t, events))
    }

    private fun hustle(s: GameState, taps: Int, events: MutableList<GameEvent>): Either {
        if (taps !in 1..50) return Either.Err(GameError.Invalid("taps"))
        val cfg = content.balance.hustle
        val now = s.simTimeMs
        val continuing = now - s.hustle.lastTapSim <= cfg.comboWindowMs
        val combo = ((if (continuing) s.hustle.combo else 0) + taps).coerceAtMost(cfg.maxCombo)
        val comboMult = Multiplier.ofDouble(1.0 + combo * cfg.comboStep)
        val frenzy = if (s.hustle.frenzyUntil > now) Multiplier.ofDouble(cfg.frenzyMultiplier) else Multiplier.ONE
        val perTap = cfg.basePerTap * (economy.hustleMultiplier(s) * comboMult * frenzy)
        // Each tap also pays a few seconds of managed income, so tapping stays worthwhile as the empire grows.
        val fromIncome = economy.totalNetRate(s, managedOnly = true).over(1) * Multiplier.ofDouble(cfg.incomeSecondsPerTap)
        val amount = (perTap + fromIncome) * taps.toLong()
        val ledger = Ledger.earn(s.ledger, now, "hustle", amount)
        events += GameEvent.HustleEarned(amount, combo)
        return Either.Ok(
            s.copy(
                ledger = ledger,
                hustle = s.hustle.copy(combo = combo, lastTapSim = now, totalTaps = s.hustle.totalTaps + taps),
                stats = s.stats.copy(lifetimeEarned = s.stats.lifetimeEarned + amount),
            ),
        )
    }

    fun frenzyChargesLeft(s: GameState): Int {
        val day = s.simTimeMs / DAY
        val used = if (s.hustle.frenzyDay == day) s.hustle.frenzyChargesUsed else 0
        return content.balance.hustle.frenzyChargesPerDay - used
    }

    private fun frenzy(s: GameState, events: MutableList<GameEvent>): Either {
        if (s.hustle.frenzyUntil > s.simTimeMs) return Either.Err(GameError.AlreadyDone)
        if (frenzyChargesLeft(s) <= 0) return Either.Err(GameError.Locked("No Frenzy charges left today"))
        val day = s.simTimeMs / DAY
        val used = if (s.hustle.frenzyDay == day) s.hustle.frenzyChargesUsed else 0
        val until = s.simTimeMs + content.balance.hustle.frenzySeconds * 1000
        events += GameEvent.FrenzyStarted(until)
        return Either.Ok(s.copy(hustle = s.hustle.copy(frenzyUntil = until, frenzyDay = day, frenzyChargesUsed = used + 1)))
    }

    private fun upgradeHq(s: GameState, events: MutableList<GameEvent>): Either {
        val next = content.balance.hqLevels.firstOrNull { it.level == s.hq.level + 1 } ?: return Either.Err(GameError.AlreadyDone)
        val paid = spend(s, next.cost, TxnCategory.Upgrade, "HQ → ${next.name}", "hq")
        if (paid is Either.Err) return paid
        events += GameEvent.HqUpgraded(next.level)
        return Either.Ok((paid as Either.Ok).state.copy(hq = s.hq.copy(level = next.level)))
    }

    private fun mothball(s: GameState, id: String, on: Boolean): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        if (b.mothballed == on) return Either.Ok(s)
        if (!on && economy.usedSlots(s) >= economy.slots(s)) return Either.Err(GameError.NoFreeSlot)
        val collected = if (on) (collect(s, id, mutableListOf()) as Either.Ok).state else s
        val nb = collected.businesses.getValue(id).copy(mothballed = on, accrual = Accrual())
        return Either.Ok(collected.copy(businesses = collected.businesses + (id to nb)))
    }

    // ----- Mechanic decisions -----

    private fun withMechanic(s: GameState, id: String, f: (BusinessState) -> Either): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        return f(b)
    }

    private fun setRetailPrice(s: GameState, id: String, pct: Int): Either = withMechanic(s, id) { b ->
        val m = content.business(id).mechanic as? MechanicDef.Retail ?: return@withMechanic Either.Err(GameError.Invalid("not retail"))
        if (b.managerId != null) return@withMechanic Either.Err(GameError.Locked("Your manager sets prices"))
        if (pct !in m.minPricePct..m.maxPricePct) return@withMechanic Either.Err(GameError.Invalid("price out of range"))
        val st = b.mechanic as? MechanicState.Retail ?: MechanicState.Retail()
        Either.Ok(s.copy(businesses = s.businesses + (id to b.copy(mechanic = st.copy(pricePct = pct)))))
    }

    fun promoCost(s: GameState, id: String): Money {
        val m = content.business(id).mechanic as MechanicDef.Retail
        return economy.businessRate(s, id).gross.over(m.promoCostSeconds).max(Money.ofDollars(5))
    }

    private fun startPromo(s: GameState, id: String): Either = withMechanic(s, id) { b ->
        val m = content.business(id).mechanic as? MechanicDef.Retail ?: return@withMechanic Either.Err(GameError.Invalid("not retail"))
        val st = b.mechanic as? MechanicState.Retail ?: MechanicState.Retail()
        if (st.promoUntil > s.simTimeMs) return@withMechanic Either.Err(GameError.AlreadyDone)
        val paid = spend(s, promoCost(s, id), TxnCategory.Purchase, "promotion", "marketing")
        if (paid is Either.Err) return@withMechanic paid
        val ps = (paid as Either.Ok).state
        val nb = ps.businesses.getValue(id).copy(mechanic = st.copy(promoUntil = ps.simTimeMs + m.promoSeconds * 1000))
        Either.Ok(ps.copy(businesses = ps.businesses + (id to nb)))
    }

    private fun setQuality(s: GameState, id: String, tierId: String): Either = withMechanic(s, id) { b ->
        val m = content.business(id).mechanic as? MechanicDef.Restaurant ?: return@withMechanic Either.Err(GameError.Invalid("not a restaurant"))
        if (b.managerId != null) return@withMechanic Either.Err(GameError.Locked("Your manager runs the kitchen"))
        if (m.qualityTiers.none { it.id == tierId }) return@withMechanic Either.Err(GameError.NotFound("tier $tierId"))
        val st = b.mechanic as? MechanicState.Restaurant ?: Mechanics.restaurantInitial(m)
        Either.Ok(s.copy(businesses = s.businesses + (id to b.copy(mechanic = st.copy(qualityTier = tierId)))))
    }

    private fun assignVehicles(s: GameState, id: String, routeId: String, count: Int): Either = withMechanic(s, id) { b ->
        val m = content.business(id).mechanic as? MechanicDef.Logistics ?: return@withMechanic Either.Err(GameError.Invalid("not logistics"))
        if (b.managerId != null) return@withMechanic Either.Err(GameError.Locked("Your manager dispatches the fleet"))
        val route = Mechanics.unlockedRoutes(m, b.level).firstOrNull { it.id == routeId } ?: return@withMechanic Either.Err(GameError.Locked("Route not unlocked"))
        val st = b.mechanic as? MechanicState.Logistics ?: MechanicState.Logistics()
        val others = st.assignments.filterKeys { it != route.id }.values.sum()
        val total = Mechanics.vehicles(m, b.level)
        if (count < 0 || others + count > total) return@withMechanic Either.Err(GameError.Invalid("not enough vehicles"))
        val newAssign = (st.assignments + (route.id to count)).filterValues { it > 0 }
        Either.Ok(s.copy(businesses = s.businesses + (id to b.copy(mechanic = MechanicState.Logistics(newAssign)))))
    }

    private fun autoPlan(s: GameState, id: String): Either = withMechanic(s, id) { b ->
        val m = content.business(id).mechanic as? MechanicDef.Logistics ?: return@withMechanic Either.Err(GameError.Invalid("not logistics"))
        // The free "auto-plan" button is a decent but not optimal plan (skill 0.85); careful manual play does better.
        val plan = Mechanics.logisticsPlan(m, b.level, 0.85)
        Either.Ok(s.copy(businesses = s.businesses + (id to b.copy(mechanic = MechanicState.Logistics(plan)))))
    }

    // ----- Module mechanics and the merger tree -----

    private fun mechanicAction(s: GameState, id: String, action: String, value: String): Either {
        val b = s.businesses[id] ?: return Either.Err(GameError.NotFound("business $id"))
        val def = content.business(id)
        val m = def.mechanic as? MechanicDef.Module ?: return Either.Err(GameError.Invalid("not a module business"))
        if (b.managerId != null && action !in MANAGER_FREE_ACTIONS) return Either.Err(GameError.Locked("Your manager is running this. Investments are still yours to make."))
        val module = MechanicRegistry.of(m.kind)
        val r = module.act(m, economy.moduleState(s, def, b, m), action, value, economy.mechCtx(s, def, b))
            .getOrElse { return Either.Err(GameError.Invalid(it.message ?: "not allowed")) }
        val paid = if (r.cost.isPositive) spend(s, r.cost, TxnCategory.Purchase, "${def.name}: $action", "operations") else Either.Ok(s)
        if (paid is Either.Err) return paid
        val ps = (paid as Either.Ok).state
        return Either.Ok(ps.copy(businesses = ps.businesses + (id to ps.businesses.getValue(id).copy(mechanic = r.state))))
    }

    private fun formConglomerate(s: GameState, cid: String, events: MutableList<GameEvent>): Either {
        val c = content.conglomerates.firstOrNull { it.id == cid } ?: return Either.Err(GameError.NotFound("conglomerate $cid"))
        economy.conglomerateBlocker(s, c)?.let { return Either.Err(if (it == "Already formed") GameError.AlreadyDone else GameError.Locked(it)) }
        val paid = spend(s, c.fee, TxnCategory.Purchase, "form ${c.name}", "mergers")
        if (paid is Either.Err) return paid
        events += GameEvent.ConglomerateFormed(cid)
        val ps = (paid as Either.Ok).state
        return Either.Ok(ps.copy(conglomerates = ps.conglomerates + cid))
    }

    private fun formHolding(s: GameState, name: String, events: MutableList<GameEvent>): Either {
        if (s.holdingName != null) return Either.Err(GameError.AlreadyDone)
        val clean = name.trim()
        if (clean.length !in 3..32) return Either.Err(GameError.Invalid("Name must be 3–32 characters"))
        if (s.conglomerates.size < 3) return Either.Err(GameError.Locked("Form three conglomerates first"))
        val paid = spend(s, content.balance.holdingFee, TxnCategory.Purchase, "form $clean", "mergers")
        if (paid is Either.Err) return paid
        events += GameEvent.HoldingFormed(clean)
        return Either.Ok((paid as Either.Ok).state.copy(holdingName = clean))
    }

    companion object {
        /** Investments a player may still make while a manager runs the routine decisions of a module business. */
        val MANAGER_FREE_ACTIONS = setOf("upgrade", "stars", "build", "roster", "reliability", "redrill", "campaign", "greenlight", "fund", "launch")
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
    }
}
