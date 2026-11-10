package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.BusinessDef
import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.ConglomerateDef
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.mechanics.MechCtx
import com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.money.Rate
import com.arnavgames.tycoon.core.state.BusinessState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.sqrt

/** Pure economy formulas (docs/ECONOMY.md §3). Everything here is deterministic and side-effect free. */
class Economy(val content: ContentPack) {
    val market = com.arnavgames.tycoon.core.market.MarketMath(content.markets)
    private val refRate = content.markets.macro.map { it.ratePerDay }.average().takeIf { !it.isNaN() } ?: 0.0
    val dynasty = com.arnavgames.tycoon.core.progression.DynastyMath(content)
    val store = com.arnavgames.tycoon.core.store.StoreEngine(content)
    val luxury = com.arnavgames.tycoon.core.luxury.LuxuryEngine(content.luxury) { s, type -> dynasty.bonus(s, type) }
    val world = com.arnavgames.tycoon.core.world.WorldMath(content) { s -> dynasty.bonus(s, "takeoverShield") }
    val credit by lazy { com.arnavgames.tycoon.core.finance.Credit(content, this) }
    val estate = com.arnavgames.tycoon.core.estate.EstateEngine(
        content.estates,
        { s -> centralRate(s) },
        { s -> luxury.setBonus(s, "rent") + dynasty.bonus(s, "rent") },
        { s -> credit.maxLtv(s) },
        { s -> credit.mortgageSpreadAdjustment(s) },
    )

    /** The central bank's rate per day in the current macro phase. */
    fun centralRate(s: GameState): Double = content.markets.macro.firstOrNull { it.id == s.market.macro.phase }?.ratePerDay ?: refRate

    // Commodity links (docs/GDD.md §6.4): business id -> (commodity id, elasticity), by mode.
    private val commodityRevenue: Map<String, List<Pair<String, Double>>> = commodityLinks("revenue")
    private val commodityUpkeep: Map<String, List<Pair<String, Double>>> = commodityLinks("upkeep")
    private val sectorsOfBusiness: Map<String, Set<String>> = content.markets.sectors
        .flatMap { s -> s.businesses.map { it to s.id } }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    private val boardSeatMultiplier = Multiplier.of(content.markets.config.boardSeatIncomeMultiplier)

    private fun commodityLinks(mode: String) = content.markets.commodities
        .flatMap { c -> c.effects.filter { it.mode == mode }.map { it.business to (c.id to it.elasticity) } }
        .groupBy({ it.first }, { it.second })

    /** Price factor from linked commodities: product of (price / anchor)^elasticity, kept within [0.75, 1.35]. */
    private fun commodityFactor(state: GameState, links: List<Pair<String, Double>>?): Double {
        if (links == null) return 1.0
        var log = 0.0
        for ((id, el) in links) log += el * (state.market.quotes[id]?.x ?: 0.0)
        return kotlin.math.exp(log).coerceIn(0.75, 1.35)
    }

    fun commodityRevenueFactor(state: GameState, businessId: String) = commodityFactor(state, commodityRevenue[businessId])
    fun commodityUpkeepFactor(state: GameState, businessId: String) = commodityFactor(state, commodityUpkeep[businessId])

    /** A board seat in a company of this business's sector gives a small income perk. */
    fun hasBoardSeatPerk(state: GameState, businessId: String): Boolean {
        if (state.market.holdings.isEmpty()) return false
        val sectors = sectorsOfBusiness[businessId] ?: return false
        return seatSectorsCached(state).any { it in sectors }
    }

    /**
     * businessRate is the hottest function in the engine, so seat sectors are memoised on the holdings map instance
     * and chapter (both only change on trades and chapter completions). The memo is one immutable snapshot, so a race
     * between threads can only cause a recomputation, never a wrong answer.
     */
    private class SeatMemo(val holdings: Map<String, *>, val chapter: Int, val sectors: Set<String>)

    @Volatile private var seatMemo: SeatMemo? = null

    private fun seatSectorsCached(state: GameState): Set<String> {
        val m = seatMemo
        if (m != null && m.holdings === state.market.holdings && m.chapter == state.progress.chapter) return m.sectors
        val sectors = market.seatSectors(state)
        seatMemo = SeatMemo(state.market.holdings, state.progress.chapter, sectors)
        return sectors
    }

    /** Opening price, discounted when the player holds a division stake in a company of the business's sector. */
    fun openCost(state: GameState, def: BusinessDef): Money {
        val sectors = sectorsOfBusiness[def.id] ?: return def.unlockCost
        return if (state.market.holdings.isNotEmpty() && market.divisionSectors(state).any { it in sectors }) {
            def.unlockCost * Multiplier.of(content.markets.config.divisionDiscount)
        } else def.unlockCost
    }

    // ---------------- Level costs ----------------

    /** Cost of buying [count] levels starting at [level]: base · g^(L−1) · (g^n − 1)/(g − 1). */
    fun levelCost(def: BusinessDef, level: Int, count: Int): Money {
        require(count >= 1)
        val g = def.costGrowth.value
        val first = growthPow(def, level - 1)
        val series = growthPow(def, count).subtract(BigDecimal.ONE).divide(g.subtract(BigDecimal.ONE), MC)
        val cents = BigDecimal(def.baseCost.cents).multiply(first, MC).multiply(series, MC)
        return Money.ofCents(cents.setScale(0, RoundingMode.CEILING).toBigIntegerExact())
    }

    /** g^n for a business's cost growth, memoised (content is immutable; pow on DECIMAL128 is the hottest path). */
    private val powCache = java.util.concurrent.ConcurrentHashMap<Long, BigDecimal>()
    private val bizIndex: Map<String, Int> = content.businesses.withIndex().associate { it.value.id to it.index }

    private fun growthPow(def: BusinessDef, n: Int): BigDecimal {
        val key = (bizIndex.getValue(def.id).toLong() shl 32) or n.toLong()
        return powCache.getOrPut(key) { def.costGrowth.value.pow(n, MC) }
    }

    /** Largest n (≤ cap) such that levelCost(level, n) ≤ budget. 0 if not even one is affordable. */
    fun maxAffordable(def: BusinessDef, level: Int, budget: Money, cap: Int = 10_000): Int {
        if (levelCost(def, level, 1) > budget) return 0
        var lo = 1
        var hi = 1
        while (hi < cap && levelCost(def, level, minOf(hi * 2, cap)) <= budget) hi = minOf(hi * 2, cap)
        if (hi == cap) return cap
        hi = minOf(hi * 2, cap)
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (levelCost(def, level, mid) <= budget) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Total spent to reach [level] (unlock + levels 1→level). Used for valuation. */
    fun investedIn(def: BusinessDef, level: Int): Money =
        def.unlockCost + if (level > 1) levelCost(def, 1, level - 1) else Money.ZERO

    fun tierMultiplier(def: BusinessDef, level: Int): Multiplier =
        Multiplier.product(def.tiers.filter { level >= it.level }.map { it.multiplier })

    fun nextTier(def: BusinessDef, level: Int) = def.tiers.firstOrNull { it.level > level }

    fun expansionMultiplier(def: BusinessDef, b: BusinessState): Multiplier =
        Multiplier.product(def.expansions.filter { it.id in b.expansionsDone }.map { it.multiplier })

    // ---------------- Managers ----------------

    fun managerSkill(def: BusinessDef, b: BusinessState): Double? {
        val m = def.managers.firstOrNull { it.id == b.managerId } ?: return null
        return (content.balance.managerSkillFloor + content.balance.managerSkillPerStar * m.stars).coerceIn(0.0, 1.0)
    }

    // ---------------- Efficiency (mechanics) ----------------

    /** Context handed to module mechanics (time, seeded randomness, owned businesses, cost scale). */
    fun mechCtx(state: GameState, def: BusinessDef, b: BusinessState, now: Long = state.simTimeMs): MechCtx = MechCtx(
        businessId = b.id,
        level = b.level,
        simTime = now,
        worldSeed = state.worldSeed,
        owned = state.businesses.keys,
        incomeSecond = Rate.perSecond(def.baseRevenue, Multiplier.of(b.level.toLong()) * tierMultiplier(def, b.level)).perSecond(),
    )

    /** Module state for a business, created on the fly if missing (e.g. a save from before the module existed). */
    fun moduleState(state: GameState, def: BusinessDef, b: BusinessState, m: MechanicDef.Module): MechanicState.Module =
        b.mechanic as? MechanicState.Module ?: MechanicRegistry.of(m.kind).initial(m, mechCtx(state, def, b))

    /** Efficiency E ∈ [0.25, 2.5] from the business's own mechanic at sim time [now]. */
    fun efficiency(state: GameState, def: BusinessDef, b: BusinessState, now: Long): Double {
        val e = when (val m = def.mechanic) {
            MechanicDef.Simple -> 1.0
            is MechanicDef.Retail -> Mechanics.retailEfficiency(m, b.mechanic as? MechanicState.Retail ?: MechanicState.Retail(), now)
            is MechanicDef.Restaurant -> Mechanics.restaurantEfficiency(m, b.mechanic as? MechanicState.Restaurant ?: Mechanics.restaurantInitial(m))
            is MechanicDef.Logistics -> Mechanics.logisticsEfficiency(m, b.level, b.mechanic as? MechanicState.Logistics ?: MechanicState.Logistics())
            is MechanicDef.Module -> MechanicRegistry.of(m.kind).efficiency(m, moduleState(state, def, b, m), mechCtx(state, def, b, now))
        }
        return e.coerceIn(E_MIN, E_MAX)
    }

    /** Extra upkeep factor from the mechanic, cross-business synergies and the business's conglomerate. */
    fun upkeepFactor(state: GameState, def: BusinessDef, b: BusinessState, now: Long = state.simTimeMs): Double {
        val own = when (val m = def.mechanic) {
            is MechanicDef.Restaurant -> {
                val st = b.mechanic as? MechanicState.Restaurant
                m.qualityTiers.firstOrNull { it.id == st?.qualityTier }?.upkeepMultiplier ?: 1.0
            }
            is MechanicDef.Module -> MechanicRegistry.of(m.kind).upkeepFactor(m, moduleState(state, def, b, m), mechCtx(state, def, b, now))
            else -> 1.0
        }
        val cong = conglomerateOf(state, b.id)?.upkeepMultiplier?.toDouble() ?: 1.0
        val hosted = if (b.id in state.estate.hosted) content.estates.config.hostedUpkeepFactor else 1.0
        return own * MechanicRegistry.crossUpkeep(state.businesses.keys, b.id) * cong * commodityUpkeepFactor(state, b.id) * hosted
    }

    // ---------------- Merger tree ----------------

    /** The formed conglomerate this business belongs to, if any. */
    fun conglomerateOf(state: GameState, businessId: String): ConglomerateDef? =
        content.conglomerates.firstOrNull { it.id in state.conglomerates && businessId in it.members }

    /** Number of Scale Tiers a business has reached. */
    fun tiersReached(def: BusinessDef, level: Int): Int = def.tiers.count { level >= it.level }

    /** Why a conglomerate can't be formed yet, or null if it can (ignoring the fee). */
    fun conglomerateBlocker(state: GameState, c: ConglomerateDef): String? {
        if (c.id in state.conglomerates) return "Already formed"
        if (state.progress.chapter < c.requiredChapter) return "Mergers open in chapter ${c.requiredChapter}"
        for (id in c.members) {
            val b = state.businesses[id] ?: return "Requires ${content.businessById[id]?.name ?: id}"
            if (b.mothballed) return "${content.business(id).name} is mothballed"
            if (tiersReached(content.business(id), b.level) < c.requiredTier) return "${content.business(id).name} needs Scale Tier ${c.requiredTier}"
            conglomerateOf(state, id)?.let { return "${content.business(id).name} already belongs to ${it.name}" }
        }
        return null
    }

    private fun crossIncome(state: GameState, id: String, now: Long): Double {
        val media = state.businesses["media"]?.mechanic as? MechanicState.Module
        return MechanicRegistry.crossIncome(state.businesses.keys, id, media, now / 60_000)
    }

    // ---------------- Rates ----------------

    /** Global multiplier G shared by all businesses (frenzy now; Legacy, Status, boosts in later phases). */
    fun globalMultiplier(state: GameState, now: Long): Multiplier {
        var g = Multiplier.ONE
        if (state.hustle.frenzyUntil > now) g *= Multiplier.ofDouble(content.balance.hustle.frenzyMultiplier)
        return g
    }

    data class BusinessRate(val gross: Rate, val upkeep: Rate, val net: Rate, val efficiency: Double)

    fun businessRate(state: GameState, id: String, now: Long = state.simTimeMs): BusinessRate {
        val b = state.businesses[id] ?: return ZERO_RATE
        if (b.mothballed) return ZERO_RATE
        val def = content.business(id)
        val e = efficiency(state, def, b, now)
        var factor = Multiplier.of(b.level.toLong()) *
            tierMultiplier(def, b.level) *
            expansionMultiplier(def, b) *
            Multiplier.ofDouble(round6(e * crossIncome(state, id, now) * commodityRevenueFactor(state, id)))
        // Market saturation: the business's own output approaches its market size (gross = M·(1 − e^(−raw/M))).
        def.marketSize?.let { m ->
            val raw = def.baseRevenue.toDollarsDouble() * factor.toDouble()
            val cap = m.toDollarsDouble()
            if (raw > 0 && cap > 0) factor *= Multiplier.ofDouble(round6(saturation(raw, cap) / raw))
        }
        factor *= globalMultiplier(state, now)
        conglomerateOf(state, id)?.let { factor *= it.incomeMultiplier }
        if (state.holdingName != null) factor *= content.balance.holdingIncomeMultiplier
        if (hasBoardSeatPerk(state, id)) factor *= boardSeatMultiplier
        luxury.incomeBonus(state, id).let { if (it > 0) factor *= Multiplier.ofDouble(1 + it) }
        world.incomeFactor(state, id).let { if (it != 1.0) factor *= Multiplier.ofDouble(it) }
        dynasty.incomeFactor(state, id).let { if (it != 1.0) factor *= Multiplier.ofDouble(it) }
        store.incomeBonus(state).let { if (it > 0) factor *= Multiplier.ofDouble(1 + it) }
        val gross = Rate.perSecond(def.baseRevenue, factor)
        val upkeepShare = (def.upkeep * Multiplier.ofDouble(round6(upkeepFactor(state, def, b, now)))).min(Multiplier.of("0.95"))
        val upkeep = gross * upkeepShare
        return BusinessRate(gross, upkeep, gross - upkeep, e)
    }

    /** M·(1 − e^(−x/M)): linear while small, flattening toward M. */
    fun saturation(x: Double, m: Double): Double = if (x / m < 1e-6) x else m * (1.0 - kotlin.math.exp(-x / m))

    fun totalNetRate(state: GameState, managedOnly: Boolean = false, now: Long = state.simTimeMs): Rate =
        state.businesses.values
            .filter { !managedOnly || it.managerId != null }
            .fold(Rate.ZERO) { acc, b -> acc + businessRate(state, b.id, now).net }

    /** Unmanaged storage cap = storageSeconds of net output. */
    fun storageCap(state: GameState, id: String, now: Long = state.simTimeMs): Money {
        val def = content.business(id)
        return businessRate(state, id, now).net.over(def.storageSeconds.toLong())
    }

    // ---------------- Slots, HQ, offline ----------------

    /** Business slots: base + HQ levels + one per story chapter reached beyond the first (docs/ECONOMY.md §3). */
    fun slots(state: GameState): Int =
        content.balance.baseSlots +
            content.balance.hqLevels.filter { it.level <= state.hq.level }.sumOf { it.extraSlots } +
            (state.progress.chapter - 1).coerceIn(0, 6) +
            dynasty.bonus(state, "slots").toInt() + state.store.extraSlots

    fun usedSlots(state: GameState): Int = state.businesses.values.count { !it.mothballed }

    fun offlineCapMs(state: GameState): Long {
        val bonus = content.balance.hqLevels.filter { it.level <= state.hq.level }.sumOf { it.offlineCapBonusSeconds } +
            (dynasty.bonus(state, "offlineHours") * 3600).toLong() + store.offlineHours(state) * 3600L
        val cap = (content.balance.offlineCapBaseSeconds + bonus).coerceAtMost(content.balance.offlineCapMaxSeconds) * 1000
        return if (dynasty.forbids(state, "halfOffline")) cap / 2 else cap
    }

    fun hustleMultiplier(state: GameState): Multiplier =
        Multiplier.product(content.balance.hqLevels.filter { it.level <= state.hq.level }.map { it.hustleMultiplier }) *
            Multiplier.ofDouble(1.0 + dynasty.bonus(state, "hustle"))

    // ---------------- Net worth ----------------

    /** Net worth = cash + 60% of capital invested in businesses (+ other asset classes in later phases). */
    fun netWorth(state: GameState): Money {
        var nw = state.cash + market.portfolioValue(state) + market.shortEquity(state)
        if (state.estate.properties.isNotEmpty()) nw += estate.portfolioValue(state) - estate.debt(state)
        if (state.luxury.owned.isNotEmpty()) nw += luxury.collectionValue(state)
        if (state.finance.loans.isNotEmpty() || state.finance.creditLine.isPositive) nw -= credit.loanDebt(state)
        for (b in state.businesses.values) {
            if (content.businessById[b.id] == null) continue
            nw += businessValue(state, b.id) + b.stored
        }
        return nw
    }

    /**
     * What a business is worth: 60% of what was invested in it, but never more than a few days of what it earns
     * (real businesses are valued on earnings; a saturated business doesn't keep getting more valuable).
     */
    fun businessValue(state: GameState, id: String): Money {
        val b = state.businesses[id] ?: return Money.ZERO
        val def = content.businessById[id] ?: return Money.ZERO
        // Book value: what was put in (1.8.2; it was 60%, so a purchase that paid back in minutes still cut net worth).
        val byCost = investedIn(def, b.level)
        val net = businessRate(state, id).net
        // Capped at a day of net earnings, so over-buying a saturated business does lose value. Mothballed or loss-making: a fifth of what its market could pay.
        val cap = if (net.perSecond().isPositive) net.over(VALUATION_SECONDS)
            else def.marketSize?.let { Rate.perSecond(it).over(VALUATION_SECONDS) * Multiplier.of("0.2") } ?: (byCost * Multiplier.of("0.2"))
        return byCost.min(cap)
    }

    companion object {
        /** Valuation multiple: one game day of net earnings. */
        const val VALUATION_SECONDS = 86_400L
        const val E_MIN = 0.25
        const val E_MAX = 2.5
        private val MC: MathContext = MathContext.DECIMAL128
        private val ZERO_RATE = BusinessRate(Rate.ZERO, Rate.ZERO, Rate.ZERO, 0.0)

        /** Doubles from mechanics are rounded to 6 decimals before becoming exact multipliers (stable, platform-independent). */
        fun round6(x: Double): Double = floor(x * 1_000_000.0 + 0.5) / 1_000_000.0
    }
}

/** The individual business mechanics (GDD §5.2). Pure functions; tested in MechanicsTest. */
object Mechanics {

    // ----- Retail: price slider along a constant-elasticity demand curve with unit costs -----
    /** Unit cost as a fraction of the reference price. Profit-optimal price = ε·c/(ε−1). */
    const val RETAIL_UNIT_COST = 0.55

    fun retailProfitFactor(m: MechanicDef.Retail, pricePct: Int): Double {
        val p = pricePct / 100.0
        val demand = StrictMath.pow(p, -m.elasticity)
        val profit = (p - RETAIL_UNIT_COST) * demand
        return profit / (1.0 - RETAIL_UNIT_COST)
    }

    fun retailOptimalPricePct(m: MechanicDef.Retail): Int {
        val raw = m.elasticity * RETAIL_UNIT_COST / (m.elasticity - 1.0) * 100.0
        return raw.toInt().coerceIn(m.minPricePct, m.maxPricePct)
    }

    fun retailEfficiency(m: MechanicDef.Retail, s: MechanicState.Retail, now: Long): Double {
        val footfall = if (s.promoUntil > now) m.promoFootfall else 1.0
        return retailProfitFactor(m, s.pricePct) * footfall
    }

    /** The price a manager with [skill] sets: moves from the reference price toward the optimum. */
    fun retailManagerPrice(m: MechanicDef.Retail, skill: Double): Int {
        val opt = retailOptimalPricePct(m)
        return (100 + (opt - 100) * skill).toInt().coerceIn(m.minPricePct, m.maxPricePct)
    }

    // ----- Restaurant: reputation stars drift toward the chosen quality tier's target -----
    fun restaurantInitial(m: MechanicDef.Restaurant) =
        MechanicState.Restaurant((m.startStars * 1000).toInt(), m.qualityTiers[m.qualityTiers.size / 2].id)

    fun restaurantEfficiency(m: MechanicDef.Restaurant, s: MechanicState.Restaurant): Double =
        m.efficiencyBase + m.efficiencyPerStar * (s.milliStars / 1000.0)

    /** Applies one game minute of reputation drift (called at every minute boundary). */
    fun restaurantMinute(m: MechanicDef.Restaurant, s: MechanicState.Restaurant): MechanicState.Restaurant {
        val target = ((m.qualityTiers.firstOrNull { it.id == s.qualityTier }?.targetStars ?: m.startStars) * 1000).toInt()
        val step = maxOf(1, (m.driftPerHour * 1000 / 60).toInt())
        val next = when {
            s.milliStars < target -> minOf(target, s.milliStars + step)
            s.milliStars > target -> maxOf(target, s.milliStars - step)
            else -> s.milliStars
        }
        return if (next == s.milliStars) s else s.copy(milliStars = next.coerceIn(1000, 5000))
    }

    /** Long-run net value of a tier (efficiency at target × (1 − upkeep share)); used by managers. */
    fun restaurantTierValue(m: MechanicDef.Restaurant, tierId: String, baseUpkeep: Double): Double {
        val t = m.qualityTiers.first { it.id == tierId }
        val e = m.efficiencyBase + m.efficiencyPerStar * t.targetStars
        return e * (1.0 - baseUpkeep * t.upkeepMultiplier)
    }

    // ----- Logistics: assign vehicles to routes; throughput = min(capacity, demand) -----
    fun vehicles(m: MechanicDef.Logistics, level: Int): Int = floor(level * m.vehiclesPerLevel).toInt().coerceAtLeast(1)

    fun routeDemand(r: com.arnavgames.tycoon.core.content.RouteDef, level: Int): Double = r.baseDemand * sqrt(level.toDouble())

    fun unlockedRoutes(m: MechanicDef.Logistics, level: Int) = m.routes.filter { it.unlockLevel <= level }

    fun logisticsEfficiency(m: MechanicDef.Logistics, level: Int, s: MechanicState.Logistics): Double {
        val v = vehicles(m, level)
        val refMargin = m.routes.first().margin
        var moved = 0.0
        for (r in unlockedRoutes(m, level)) {
            val assigned = (s.assignments[r.id] ?: 0).coerceAtLeast(0)
            moved += minOf(assigned * m.vehicleCapacity, routeDemand(r, level)) * r.margin
        }
        return moved / (v * m.vehicleCapacity * refMargin)
    }

    fun assignedTotal(s: MechanicState.Logistics) = s.assignments.values.sum()

    /**
     * Assigns vehicles one at a time to the route with the largest marginal gain (min(capacity, unmet demand) × margin).
     * Each route's value is concave in vehicles, so this greedy is optimal at skill = 1. A manager with [skill] < 1 plans
     * only that share of the fleet; the rest stays on the default (first) route, modelling an imperfect plan.
     */
    fun logisticsPlan(m: MechanicDef.Logistics, level: Int, skill: Double): Map<String, Int> {
        val total = vehicles(m, level)
        val routes = unlockedRoutes(m, level)
        val smart = (total * skill.coerceIn(0.0, 1.0)).toInt()
        val assigned = IntArray(routes.size)
        repeat(smart) {
            var best = -1
            var bestGain = 0.0
            for (i in routes.indices) {
                val unmet = routeDemand(routes[i], level) - assigned[i] * m.vehicleCapacity
                val gain = minOf(m.vehicleCapacity, unmet) * routes[i].margin
                if (gain > bestGain + 1e-12) { bestGain = gain; best = i }
            }
            assigned[if (best >= 0) best else 0]++
        }
        assigned[0] += total - smart
        return routes.indices.filter { assigned[it] > 0 }.associate { routes[it].id to assigned[it] }
    }
}
