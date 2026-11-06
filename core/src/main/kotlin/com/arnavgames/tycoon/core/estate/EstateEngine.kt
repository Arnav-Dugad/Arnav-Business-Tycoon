package com.arnavgames.tycoon.core.estate

import com.arnavgames.tycoon.core.content.EstateContent
import com.arnavgames.tycoon.core.content.PropertyTypeDef
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.LedgerState
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.EstateState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.NewsItem
import com.arnavgames.tycoon.core.state.PropertyState
import com.arnavgames.tycoon.core.state.TenantEventState
import com.arnavgames.tycoon.core.state.TimerKind
import com.arnavgames.tycoon.core.state.TimerState
import com.arnavgames.tycoon.core.state.TowerState
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Real estate (docs/GDD.md §7, docs/ECONOMY.md §6). District price indices and listings are pure functions of the
 * world seed and time; rent, tax, insurance, mortgage interest, disasters and tenant decisions are settled every 6 hours
 * on the same boundaries as market payouts, so offline catch-up equals live play.
 */
class EstateEngine(
    val c: EstateContent,
    private val ratePerDay: (GameState) -> Double,
    private val rentBonus: (GameState) -> Double = { 0.0 },
    /** Highest loan-to-value the player's credit rating allows (Phase 9). */
    val maxLtv: (GameState) -> Double = { 1.0 },
    /** Mortgage spread adjustment for the player's credit rating, per day. */
    private val spreadAdjustment: (GameState) -> Double = { 0.0 },
) {
    /** Loan-to-value choices the player's rating allows right now. */
    fun ltvOptions(state: GameState): List<Double> = cfg.mortgageLtvOptions.filter { it <= maxLtv(state) + 1e-9 }

    private val cfg = c.config
    private val cityScale: Map<String, BigDecimal> = c.cities.associate { it.id to BigDecimal(it.priceScale) }

    // =====================================================================================
    // Prices
    // =====================================================================================

    fun isUnlocked(state: GameState, feature: String) = state.progress.chapter >= (cfg.unlocks[feature] ?: 99)
    fun unlockChapter(feature: String) = cfg.unlocks[feature] ?: 99
    fun cityUnlocked(state: GameState, cityId: String) = isUnlocked(state, "estates") && state.progress.chapter >= (c.cityById[cityId]?.chapter ?: 99)

    /** District price index: drifting trend × district cycle × city cycle × macro swing × permanent boosts. */
    fun priceIndex(state: GameState, districtId: String, simMs: Long = state.simTimeMs): Double {
        val d = c.districtById[districtId] ?: return 1.0
        val city = c.cityById.getValue(d.city)
        val days = simMs / 86_400_000.0
        val macro = cfg.macroAmplitude[state.market.macro.phase] ?: 0.0
        val boost = state.estate.districtBoost[districtId] ?: 0.0
        return exp(d.trendPerDay * days + boost) *
            (1 + d.amplitude * sin(2 * PI * days / d.periodDays + d.phase)) *
            (1 + city.amplitude * sin(2 * PI * days / city.periodDays + city.phase)) *
            (1 + macro)
    }

    /** "Rising", "Peak", "Falling" or "Bottom" from the district cycle's slope and level. */
    fun cycleWord(state: GameState, districtId: String): String {
        val d = c.districtById.getValue(districtId)
        val ang = 2 * PI * (state.simTimeMs / 86_400_000.0) / d.periodDays + d.phase
        val s = sin(ang)
        val slope = kotlin.math.cos(ang)
        return when {
            s > 0.7 -> "Peak"
            s < -0.7 -> "Bottom"
            slope > 0 -> "Rising"
            else -> "Falling"
        }
    }

    private fun conditionFactor(condition: Int) = 0.55 + 0.45 * condition / 1000.0

    private fun renovationMult(t: PropertyTypeDef, renovations: Int): Double {
        var m = 1.0
        repeat(renovations) { m *= 1 + t.renovationValue }
        return m
    }

    private fun base(districtId: String, typeId: String, size: Double): Money {
        val d = c.districtById.getValue(districtId)
        val t = c.typeById.getValue(typeId)
        return t.basePrice * Multiplier.of(cityScale.getValue(d.city)) * Multiplier.ofDouble(d.tier * size)
    }

    fun propertyValue(state: GameState, p: PropertyState, simMs: Long = state.simTimeMs): Money {
        val t = c.typeById.getValue(p.type)
        val pi = priceIndex(state, p.district, simMs)
        var v = base(p.district, p.type, p.size) * Multiplier.ofDouble(pi * conditionFactor(p.condition) * renovationMult(t, p.renovations))
        p.tower?.let { tw -> v += towerValue(state, p, tw, pi) }
        return v
    }

    private fun towerValue(state: GameState, p: PropertyState, tw: TowerState, pi: Double): Money {
        val design = c.towerById.getValue(tw.design)
        val scale = Multiplier.of(cityScale.getValue(c.districtById.getValue(p.district).city))
        return if (tw.stagesDone >= design.stageCosts.size) design.value * scale * Multiplier.ofDouble(pi)
        else design.stageCosts.take(tw.stagesDone).fold(Money.ZERO) { a, m -> a + m } * scale * Multiplier.of("0.8")
    }

    /** Market rent for one 6-hour period before occupancy, decisions and boosts. */
    fun marketRent6h(state: GameState, p: PropertyState, simMs: Long = state.simTimeMs): Money {
        val d = c.districtById.getValue(p.district)
        val t = c.typeById.getValue(p.type)
        val pi = priceIndex(state, p.district, simMs)
        val landValue = base(p.district, p.type, p.size) * Multiplier.ofDouble(pi * conditionFactor(p.condition) * renovationMult(t, p.renovations))
        var rent = landValue * Multiplier.ofDouble(d.yieldPerDay * t.yieldMultiplier * rentRenovationMult(t, p.renovations) / renovationMult(t, p.renovations) / 4)
        val tw = p.tower
        if (tw != null) {
            val design = c.towerById.getValue(tw.design)
            if (tw.stagesDone >= design.stageCosts.size) {
                val leased = ((simMs - tw.completedAtSim) / 3_600_000L).coerceIn(0, design.floors.toLong())
                rent += towerValue(state, p, tw, pi) * Multiplier.ofDouble(design.floorRentPerDay / 4 * leased / design.floors)
            }
        }
        return rent
    }

    private fun rentRenovationMult(t: PropertyTypeDef, renovations: Int): Double {
        var m = 1.0
        repeat(renovations) { m *= 1 + t.renovationRent }
        return m
    }

    /** Share of market rent collected at a given asking rent: higher rent, fewer takers. Peaks near 100 %. */
    fun rentFactor(rentPct: Int): Double {
        val r = rentPct / 100.0
        val occupancy = (1.0 - 1.2 * (r - 0.95)).coerceIn(0.3, 1.0)
        return r * occupancy
    }

    /** Hours until a tenant moves in at this asking rent. */
    fun vacancyHours(rentPct: Int): Double = 1.0 * Math.pow(rentPct / 80.0, 3.0) + 0.5

    /** What the property will pay at the next payout (zero if vacant, being worked on, for sale or hosting a business). */
    fun expectedRent6h(state: GameState, p: PropertyState, simMs: Long = state.simTimeMs): Money {
        if (p.hostedBusiness != null || p.tenantFrom > simMs || busy(state, p.id)) return Money.ZERO
        var f = rentFactor(p.rentPct)
        if (p.boostUntil > simMs) f *= if (p.boostSign >= 0) 1.1 else 0.9
        if (state.estate.events.any { it.propertyId == p.id }) f *= cfg.unresolvedRentFactor
        f *= 1 + rentBonus(state)
        return marketRent6h(state, p, simMs) * Multiplier.ofDouble(f)
    }

    fun busy(state: GameState, propertyId: Long): Boolean = state.timers.any { it.targetId == "p$propertyId" && it.kind in ESTATE_TIMERS }

    fun portfolioValue(state: GameState): Money = state.estate.properties.values.fold(Money.ZERO) { a, p -> a + propertyValue(state, p) }

    fun debt(state: GameState): Money = state.estate.properties.values.fold(Money.ZERO) { a, p -> a + p.mortgage }

    // =====================================================================================
    // Listings: a pure function of (seed, district, 6-hour epoch), minus what was bought
    // =====================================================================================

    data class Listing(val id: String, val district: String, val type: String, val size: Double, val condition: Int, val distressed: Boolean, val discount: Double, val price: Money)

    fun epoch(simMs: Long) = simMs / (cfg.listingRefreshHours * 3_600_000L)

    fun listings(state: GameState, districtId: String): List<Listing> {
        val d = c.districtById[districtId] ?: return emptyList()
        val ep = epoch(state.simTimeMs)
        val r = GameRandom.stream(state.worldSeed, "listings:$districtId", ep)
        val n = r.nextInt(cfg.listingsMin, cfg.listingsMax + 1)
        val pi = priceIndex(state, districtId)
        return (0 until n).mapNotNull { i ->
            val type = d.types[r.nextInt(d.types.size)]
            val size = Math.round(r.nextDouble(0.6, 2.2) * 100) / 100.0
            val distressed = r.chance(cfg.distressedChance)
            val condition = if (distressed) r.nextInt(300, 560) else r.nextInt(600, 1001)
            val discount = if (distressed) r.nextDouble(cfg.distressedDiscountMin, cfg.distressedDiscountMax) else 1.0
            val id = "$districtId:$ep:$i"
            if (id in state.estate.boughtListings) return@mapNotNull null
            val t = c.typeById.getValue(type)
            val value = base(districtId, type, size) * Multiplier.ofDouble(pi * conditionFactor(condition) * renovationMult(t, 0) * discount)
            Listing(id, districtId, type, size, condition, distressed, discount, value)
        }
    }

    // =====================================================================================
    // Commands
    // =====================================================================================

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    private fun err(e: GameError) = Result.Err(e)

    private fun post(state: GameState, memo: String, postings: List<Posting>, floors: Map<String, Money> = Ledger.DEFAULT_FLOORS): Pair<LedgerState?, GameError?> =
        when (val r = Ledger.post(state.ledger, state.simTimeMs, TxnCategory.Property, memo, postings, floors)) {
            is Ledger.Result.Ok -> r.state to null
            is Ledger.Result.Err -> null to when (val e = r.error) { is LedgerError.InsufficientFunds -> GameError.InsufficientFunds(e.needed, e.available) }
        }

    private fun property(state: GameState, id: Long): PropertyState? = state.estate.properties[id]

    private fun withProperty(state: GameState, p: PropertyState) = state.copy(estate = state.estate.copy(properties = state.estate.properties + (p.id to p)))

    fun buyCost(listing: Listing, ltv: Double): Pair<Money, Money> {
        val loan = listing.price * Multiplier.ofDouble(ltv)
        val fee = listing.price * Multiplier.ofDouble(cfg.transactionCost)
        return (listing.price - loan + fee) to loan
    }

    fun buy(state: GameState, listingId: String, ltv: Double, insured: Boolean, events: MutableList<GameEvent>): Result {
        if (!isUnlocked(state, "estates")) return err(GameError.Locked("Estates open in chapter ${unlockChapter("estates")}"))
        val districtId = listingId.substringBefore(':')
        val d = c.districtById[districtId] ?: return err(GameError.NotFound("district"))
        if (!cityUnlocked(state, d.city)) return err(GameError.Locked("${c.cityById.getValue(d.city).name} opens in chapter ${c.cityById.getValue(d.city).chapter}"))
        val listing = listings(state, districtId).firstOrNull { it.id == listingId } ?: return err(GameError.NotFound("That listing has gone"))
        if (ltv !in cfg.mortgageLtvOptions) return err(GameError.Invalid("Unsupported loan-to-value"))
        if (ltv > 0 && !isUnlocked(state, "mortgages")) return err(GameError.Locked("Mortgages open in chapter ${unlockChapter("mortgages")}"))
        if (ltv > maxLtv(state) + 1e-9) return err(GameError.Locked("Your credit rating allows at most ${(maxLtv(state) * 100).toInt()}% loan-to-value"))
        if (state.estate.properties.size >= cfg.maxProperties) return err(GameError.Invalid("You can own at most ${cfg.maxProperties} properties"))
        val (cash, loan) = buyCost(listing, ltv)
        val fee = listing.price * Multiplier.ofDouble(cfg.transactionCost)
        val (ledger, e) = post(state, "buy ${c.typeById.getValue(listing.type).name} in ${d.name}", listOf(
            Posting(Accounts.CASH, -cash),
            Posting(PROPERTY, listing.price),
            Posting(MORTGAGES, -loan),
            Posting(Accounts.expense("property_fees"), fee),
        ))
        if (ledger == null) return err(e!!)
        val id = state.estate.nextPropertyId
        val p = PropertyState(
            id = id, district = districtId, type = listing.type, size = listing.size, condition = listing.condition,
            costBasis = listing.price, mortgage = loan, insured = insured,
            tenantFrom = state.simTimeMs + (vacancyHours(100) * 3_600_000).toLong(), boughtAtSim = state.simTimeMs,
        )
        events += GameEvent.PropertyBought(id, listing.price)
        val ep = epoch(state.simTimeMs)
        return Result.Ok(state.copy(ledger = ledger, estate = state.estate.copy(
            properties = state.estate.properties + (id to p), nextPropertyId = id + 1,
            boughtListings = state.estate.boughtListings.filter { it.split(':').getOrNull(1)?.toLongOrNull() == ep }.toSet() + listingId,
        )))
    }

    val RENT_LEVELS = listOf(80, 90, 100, 110, 120)

    fun setRent(state: GameState, id: Long, pct: Int): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (pct !in RENT_LEVELS) return err(GameError.Invalid("rent level"))
        if (p.hostedBusiness != null) return err(GameError.Invalid("Your business operates from here"))
        if (p.rentPct == pct) return Result.Ok(state)
        // A new asking rent means finding a new tenant.
        val start = maxOf(state.simTimeMs, timerEnd(state, id) ?: 0)
        return Result.Ok(withProperty(state, p.copy(rentPct = pct, tenantFrom = start + (vacancyHours(pct) * 3_600_000).toLong())))
    }

    private fun timerEnd(state: GameState, id: Long) = state.timers.filter { it.targetId == "p$id" && it.kind in ESTATE_TIMERS }.maxOfOrNull { it.endSim }

    fun renovationCost(state: GameState, p: PropertyState, repair: Boolean): Money =
        propertyValue(state, p) * Multiplier.ofDouble(if (repair) cfg.repairCost else c.typeById.getValue(p.type).renovationCost)

    fun renovate(state: GameState, id: Long, repair: Boolean, events: MutableList<GameEvent>): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (busy(state, id)) return err(GameError.Invalid("Work or a sale is already under way"))
        val t = c.typeById.getValue(p.type)
        if (repair && p.condition >= 1000) return err(GameError.AlreadyDone)
        if (!repair && p.renovations >= cfg.maxRenovations) return err(GameError.Invalid("Fully renovated"))
        val cost = renovationCost(state, p, repair)
        val (ledger, e) = post(state, (if (repair) "repair " else "renovate ") + t.name, listOf(Posting(Accounts.CASH, -cost), Posting(Accounts.expense("renovations"), cost)))
        if (ledger == null) return err(e!!)
        val hours = if (repair) cfg.repairHours else t.renovationHours
        val end = state.simTimeMs + hours * 3_600_000L
        val timer = TimerState(state.nextTimerId, TimerKind.Renovation, "p$id", if (repair) "repair" else "renovate", state.simTimeMs, end)
        events += GameEvent.RenovationStarted(id, end)
        // The tenant moves out while the builders are in; a new one arrives after the work.
        val np = if (p.hostedBusiness == null) p.copy(tenantFrom = end + (vacancyHours(p.rentPct) * 3_600_000).toLong()) else p
        return Result.Ok(withProperty(state.copy(ledger = ledger, timers = state.timers + timer, nextTimerId = state.nextTimerId + 1), np))
    }

    fun startSale(state: GameState, id: Long, events: MutableList<GameEvent>): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (busy(state, id)) return err(GameError.Invalid("Work or a sale is already under way"))
        if (p.tower != null && p.tower.stagesDone in 1 until c.towerById.getValue(p.tower.design).stageCosts.size) return err(GameError.Invalid("Finish the tower before selling"))
        val p2 = p.copy(hostedBusiness = null)
        val hours = c.typeById.getValue(p.type).saleHours
        val timer = TimerState(state.nextTimerId, TimerKind.Sale, "p$id", "", state.simTimeMs, state.simTimeMs + hours * 3_600_000L)
        events += GameEvent.SaleListed(id, timer.endSim)
        val hosted = state.estate.hosted.filterValues { it != id }
        return Result.Ok(state.copy(timers = state.timers + timer, nextTimerId = state.nextTimerId + 1, estate = state.estate.copy(properties = state.estate.properties + (id to p2), hosted = hosted)))
    }

    fun cancelSale(state: GameState, id: Long): Result {
        val t = state.timers.firstOrNull { it.kind == TimerKind.Sale && it.targetId == "p$id" } ?: return err(GameError.NotFound("No sale under way"))
        return Result.Ok(state.copy(timers = state.timers - t))
    }

    /** Proceeds of selling now: value minus transaction cost, mortgage and capital-gains tax. */
    data class SaleQuote(val gross: Money, val fee: Money, val mortgage: Money, val tax: Money, val net: Money, val gain: Money)

    fun saleQuote(state: GameState, p: PropertyState, taxRate: Double): SaleQuote {
        val gross = propertyValue(state, p)
        val fee = gross * Multiplier.ofDouble(cfg.transactionCost)
        val gain = gross - p.costBasis
        val taxable = gross - fee - p.costBasis
        val tax = if (taxable.isPositive) taxable * Multiplier.ofDouble(taxRate) else Money.ZERO
        val net = (gross - fee - tax - p.mortgage).max(Money.ZERO)
        return SaleQuote(gross, fee, p.mortgage, tax, net, gain)
    }

    /** Sells every property at once (Phoenix protocol): the bank is repaid first and absorbs any shortfall. */
    fun liquidateAll(state: GameState, taxRate: Double, events: MutableList<GameEvent>): GameState =
        state.estate.properties.values.sortedBy { it.id }.fold(state) { s, p -> completeSale(s, p, taxRate, "orderly liquidation", events) }

    private fun completeSale(state: GameState, p: PropertyState, taxRate: Double, memo: String, events: MutableList<GameEvent>): GameState {
        val q = saleQuote(state, p, taxRate)
        // The bank is repaid first; if proceeds fall short (foreclosure), the bank absorbs the gap.
        val available = q.gross - q.fee - q.tax
        val repaid = p.mortgage.min(available.max(Money.ZERO))
        val shortfall = p.mortgage - repaid
        val postings = listOf(
            Posting(Accounts.CASH, q.net),
            Posting(PROPERTY, -p.costBasis),
            Posting(MORTGAGES, p.mortgage),
            Posting(Accounts.expense("property_fees"), q.fee),
            Posting(Accounts.expense("tax"), q.tax),
            Posting(Accounts.revenue("property_gains"), -q.gain),
            Posting(Accounts.WORLD, -shortfall),
        )
        val ledger = (Ledger.post(state.ledger, state.simTimeMs, TxnCategory.Property, memo, postings, emptyMap()) as Ledger.Result.Ok).state
        events += GameEvent.PropertySold(p.id, q.net, q.gain)
        return state.copy(
            ledger = ledger,
            timers = state.timers.filterNot { it.targetId == "p${p.id}" && it.kind in ESTATE_TIMERS },
            estate = state.estate.copy(
                properties = state.estate.properties - p.id,
                hosted = state.estate.hosted.filterValues { it != p.id },
                events = state.estate.events.filterNot { it.propertyId == p.id },
            ),
        )
    }

    fun setInsured(state: GameState, id: Long, on: Boolean): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        return Result.Ok(withProperty(state, p.copy(insured = on)))
    }

    fun repay(state: GameState, id: Long): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (!p.mortgage.isPositive) return err(GameError.AlreadyDone)
        val (ledger, e) = post(state, "repay mortgage", listOf(Posting(Accounts.CASH, -p.mortgage), Posting(MORTGAGES, p.mortgage)))
        if (ledger == null) return err(e!!)
        return Result.Ok(withProperty(state.copy(ledger = ledger), p.copy(mortgage = Money.ZERO)))
    }

    /** Run a business from your own property: the business saves rent (lower upkeep) and the property earns none. */
    fun host(state: GameState, id: Long, businessId: String?): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (businessId == null) {
            val hosted = state.estate.hosted.filterValues { it != id }
            val np = p.copy(hostedBusiness = null, tenantFrom = state.simTimeMs + (vacancyHours(p.rentPct) * 3_600_000).toLong())
            return Result.Ok(state.copy(estate = state.estate.copy(properties = state.estate.properties + (id to np), hosted = hosted)))
        }
        if (businessId !in state.businesses) return err(GameError.NotFound("You don't own that business"))
        if (businessId !in c.typeById.getValue(p.type).hosts) return err(GameError.Invalid("That business can't operate from a ${c.typeById.getValue(p.type).name.lowercase()}"))
        if (state.timers.any { it.kind == TimerKind.Sale && it.targetId == "p$id" }) return err(GameError.Invalid("It's for sale"))
        // One property per business; the previous one goes back to renting.
        var props = state.estate.properties
        state.estate.hosted[businessId]?.let { old -> props[old]?.let { op -> props = props + (old to op.copy(hostedBusiness = null, tenantFrom = state.simTimeMs + 3_600_000)) } }
        val hosted = state.estate.hosted.filterValues { it != id } + (businessId to id)
        props = props + (id to p.copy(hostedBusiness = businessId))
        return Result.Ok(state.copy(estate = state.estate.copy(properties = props, hosted = hosted)))
    }

    fun towerStageCost(state: GameState, p: PropertyState, designId: String, stage: Int): Money {
        val design = c.towerById.getValue(designId)
        val scale = Multiplier.of(cityScale.getValue(c.districtById.getValue(p.district).city))
        val discount = if ("construction" in state.businesses) Multiplier.ofDouble(cfg.towerConstructionDiscount) else Multiplier.ONE
        return design.stageCosts[stage] * scale * discount
    }

    fun buildTower(state: GameState, id: Long, designId: String, events: MutableList<GameEvent>): Result {
        val p = property(state, id) ?: return err(GameError.NotFound("property"))
        if (!isUnlocked(state, "skyscrapers")) return err(GameError.Locked("Skyscrapers open in chapter ${unlockChapter("skyscrapers")}"))
        val d = c.districtById.getValue(p.district)
        if (p.type != "land" || !d.cbd) return err(GameError.Invalid("Towers need land in a central business district"))
        if (busy(state, id)) return err(GameError.Invalid("Work or a sale is already under way"))
        val design = c.towerById[p.tower?.design ?: designId] ?: return err(GameError.NotFound("design"))
        val tw = p.tower ?: TowerState(design.id)
        if (tw.stagesDone >= design.stageCosts.size) return err(GameError.AlreadyDone)
        val cost = towerStageCost(state, p, design.id, tw.stagesDone)
        val (ledger, e) = post(state, "${design.name}: ${design.stageNames[tw.stagesDone]}", listOf(Posting(Accounts.CASH, -cost), Posting(Accounts.expense("construction"), cost)))
        if (ledger == null) return err(e!!)
        val end = state.simTimeMs + design.stageHours[tw.stagesDone] * 3_600_000L
        val timer = TimerState(state.nextTimerId, TimerKind.TowerStage, "p$id", design.id, state.simTimeMs, end)
        events += GameEvent.TowerStageStarted(id, tw.stagesDone, end)
        return Result.Ok(withProperty(state.copy(ledger = ledger, timers = state.timers + timer, nextTimerId = state.nextTimerId + 1), p.copy(tower = tw)))
    }

    fun resolveEvent(state: GameState, eventId: Long, choice: Int, events: MutableList<GameEvent>): Result {
        val ev = state.estate.events.firstOrNull { it.id == eventId } ?: return err(GameError.NotFound("decision"))
        val def = c.eventById.getValue(ev.event)
        val ch = def.choices.getOrNull(choice) ?: return err(GameError.Invalid("choice"))
        val p = property(state, ev.propertyId) ?: return Result.Ok(state.copy(estate = state.estate.copy(events = state.estate.events - ev)))
        val rent = marketRent6h(state, p)
        val cost = rent * Multiplier.ofDouble(ch.costRent)
        val gain = rent * Multiplier.ofDouble(ch.gainRent)
        var s = state
        if (cost.isPositive || gain.isPositive) {
            val (ledger, e) = post(s, "tenant: ${ch.label}", listOf(
                Posting(Accounts.CASH, gain - cost),
                Posting(Accounts.expense("property_upkeep"), cost),
                Posting(Accounts.revenue("rent"), -gain),
            ))
            if (ledger == null) return err(e!!)
            s = s.copy(ledger = ledger)
        }
        var np = p.copy(condition = (p.condition + ch.condition).coerceIn(200, 1000))
        if (ch.vacancyHours > 0) np = np.copy(tenantFrom = maxOf(np.tenantFrom, state.simTimeMs + ch.vacancyHours * 3_600_000L))
        if (ch.boostDays != 0.0) np = np.copy(boostUntil = state.simTimeMs + (kotlin.math.abs(ch.boostDays) * 86_400_000).toLong(), boostSign = if (ch.boostDays > 0) 1 else -1)
        events += GameEvent.TenantDecision(ev.id)
        return Result.Ok(withProperty(s.copy(estate = s.estate.copy(events = s.estate.events - ev)), np))
    }

    // =====================================================================================
    // Timers and the 6-hourly settlement
    // =====================================================================================

    fun completeTimer(state: GameState, t: TimerState, taxRate: Double, events: MutableList<GameEvent>): GameState {
        val id = t.targetId.removePrefix("p").toLongOrNull() ?: return state
        val p = state.estate.properties[id] ?: return state
        return when (t.kind) {
            TimerKind.Renovation -> {
                events += GameEvent.RenovationCompleted(id)
                withProperty(state, if (t.detailId == "repair") p.copy(condition = 1000) else p.copy(condition = 1000, renovations = p.renovations + 1))
            }
            TimerKind.Sale -> completeSale(state, p, taxRate, "sell ${c.typeById.getValue(p.type).name}", events)
            TimerKind.TowerStage -> {
                val tw = p.tower ?: return state
                val design = c.towerById.getValue(tw.design)
                val done = tw.stagesDone + 1
                var s = withProperty(state, p.copy(tower = tw.copy(stagesDone = done, completedAtSim = if (done >= design.stageCosts.size) state.simTimeMs else 0)))
                if (done >= design.stageCosts.size) {
                    // A finished landmark lifts the whole district.
                    val boost = (s.estate.districtBoost[p.district] ?: 0.0) + kotlin.math.ln(1 + design.landmarkBoost)
                    s = s.copy(estate = s.estate.copy(districtBoost = s.estate.districtBoost + (p.district to boost)))
                    s = news(s, "${design.name} opens in ${c.districtById.getValue(p.district).name}. Property prices nearby jump.", p.district)
                    events += GameEvent.TowerCompleted(id)
                } else events += GameEvent.TowerStageCompleted(id, done)
                s
            }
            else -> state
        }
    }

    private fun news(state: GameState, text: String, target: String): GameState {
        val mk = state.market
        val item = NewsItem(mk.nextNewsId, mk.tick.coerceAtLeast(0), text, "estate", target)
        return state.copy(market = mk.copy(news = (mk.news + item).takeLast(40), nextNewsId = mk.nextNewsId + 1))
    }

    fun tick(state: GameState, minute: Long, taxRate: Double, events: MutableList<GameEvent>): GameState {
        if (c.districts.isEmpty() || minute <= state.estate.tick) return state
        var s = state.copy(estate = state.estate.copy(tick = minute))
        if (minute > 0 && minute % (24 * 60) == 0L) s = gentrify(s, minute)
        if (minute % (6 * 60) == 0L && s.estate.properties.isNotEmpty()) s = settle(s, minute, taxRate, events)
        return s
    }

    private fun gentrify(state: GameState, minute: Long): GameState {
        val r = GameRandom.stream(state.worldSeed, "gentrify", minute)
        var s = state
        for (d in c.districts) {
            if (!r.chance(cfg.gentrificationChancePerDay)) continue
            val boost = (s.estate.districtBoost[d.id] ?: 0.0) + kotlin.math.ln(1 + cfg.gentrificationBoost)
            s = s.copy(estate = s.estate.copy(districtBoost = s.estate.districtBoost + (d.id to boost)))
            s = news(s, "${d.name} in ${c.cityById.getValue(d.city).name} is the new place to live. Prices there rise.", d.id)
        }
        return s
    }

    private fun settle(state: GameState, minute: Long, taxRate: Double, events: MutableList<GameEvent>): GameState {
        var s = state
        val time = minute * 60_000
        val r = GameRandom.stream(state.worldSeed, "estate_settle", minute)
        val rate = (ratePerDay(state) + cfg.mortgageSpreadPerDay + spreadAdjustment(state)).coerceAtLeast(0.0)
        var rent = Money.ZERO
        var tax = Money.ZERO
        var insurance = Money.ZERO
        var interestPaid = Money.ZERO
        val props = s.estate.properties.toMutableMap()
        val newEvents = s.estate.events.toMutableList()
        var nextEventId = s.estate.nextEventId
        for (id in props.keys.sorted()) {
            var p = props.getValue(id)
            val value = propertyValue(s, p)
            rent += expectedRent6h(s, p)
            tax += value * Multiplier.ofDouble(cfg.propertyTaxPerDay / 4)
            if (p.insured) insurance += value * Multiplier.ofDouble(cfg.insurancePerDay / 4)
            if (p.mortgage.isPositive) {
                val interest = p.mortgage * Multiplier.ofDouble(rate / 4)
                interestPaid += interest
            }
            // Disasters: insured properties are repaired by the insurer; others lose condition until repaired.
            val d = c.districtById.getValue(p.district)
            val t = c.typeById.getValue(p.type)
            if (r.nextDouble() < cfg.disasterChancePer6h * d.risk * t.risk) {
                if (!p.insured) p = p.copy(condition = (p.condition - r.nextInt(300, 601)).coerceAtLeast(200))
                events += GameEvent.Disaster(id, p.insured)
            }
            // Tenant decisions queue up (at most three); they never resolve themselves.
            val rented = p.hostedBusiness == null && p.tenantFrom <= s.simTimeMs
            if (rented && newEvents.size < cfg.maxQueuedEvents && newEvents.none { it.propertyId == id } &&
                r.nextDouble() < cfg.eventChancePer6h * p.rentPct / 100.0
            ) {
                val def = c.events[r.nextInt(c.events.size)]
                newEvents += TenantEventState(nextEventId++, id, def.id, s.simTimeMs)
                events += GameEvent.TenantEventRaised(id)
            }
            props[id] = p
        }
        s = s.copy(estate = s.estate.copy(properties = props, events = newEvents, nextEventId = nextEventId))
        var ledger = s.ledger
        if (rent.isPositive) ledger = Ledger.earn(ledger, time, "rent", rent)
        val costs = tax + insurance
        if (costs.isPositive) {
            ledger = (Ledger.post(ledger, time, TxnCategory.Property, "property tax and insurance", listOf(
                Posting(Accounts.CASH, -costs), Posting(Accounts.expense("property_tax"), tax), Posting(Accounts.expense("insurance"), insurance),
            ), emptyMap()) as Ledger.Result.Ok).state
        }
        s = s.copy(ledger = ledger)
        // Mortgage interest: paid from cash; what cash can't cover is added to the loan.
        if (interestPaid.isPositive) {
            val fromCash = interestPaid.min(s.cash.max(Money.ZERO))
            val capitalised = interestPaid - fromCash
            ledger = (Ledger.post(s.ledger, time, TxnCategory.Interest, "mortgage interest", listOf(
                Posting(Accounts.CASH, -fromCash), Posting(MORTGAGES, -capitalised), Posting(Accounts.expense("interest"), interestPaid),
            ), emptyMap()) as Ledger.Result.Ok).state
            s = s.copy(ledger = ledger)
            if (capitalised.isPositive) {
                events += GameEvent.PaymentMissed("mortgage", capitalised)
                // Spread the capitalised interest over the mortgaged properties in proportion to their loans.
                val mortgaged = s.estate.properties.values.filter { it.mortgage.isPositive }.sortedBy { it.id }
                val total = mortgaged.fold(Money.ZERO) { a, p -> a + p.mortgage }
                var left = capitalised
                val updated = s.estate.properties.toMutableMap()
                mortgaged.forEachIndexed { i, p ->
                    val share = if (i == mortgaged.lastIndex) left else Money.ofCents(capitalised.cents.multiply(p.mortgage.cents).divide(total.cents))
                    left -= share
                    updated[p.id] = p.copy(mortgage = p.mortgage + share)
                }
                s = s.copy(estate = s.estate.copy(properties = updated))
            }
        }
        // Foreclosure: when a loan reaches 95 % of the property's value, the bank sells it.
        for (p in s.estate.properties.values.sortedBy { it.id }) {
            if (p.mortgage.isPositive && p.mortgage.toDollarsDouble() >= cfg.foreclosureLtv * propertyValue(s, p).toDollarsDouble()) {
                s = completeSale(s, p, taxRate, "foreclosure", events)
                events += GameEvent.Foreclosure(p.id)
            }
        }
        return s
    }

    companion object {
        const val PROPERTY = "asset:property"
        const val MORTGAGES = "liability:mortgages"
        val ESTATE_TIMERS = setOf(TimerKind.Renovation, TimerKind.Sale, TimerKind.TowerStage)
    }
}
