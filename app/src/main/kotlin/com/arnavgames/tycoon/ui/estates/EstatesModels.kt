package com.arnavgames.tycoon.ui.estates

import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.PropertyState
import com.arnavgames.tycoon.core.state.TimerKind

data class CityPinUi(val id: String, val name: String, val x: Float, val y: Float, val unlocked: Boolean, val chapter: Int, val districts: Int, val owned: Int)

data class PropertyRowUi(
    val id: Long, val title: String, val subtitle: String, val icon: String, val value: Money, val rent6h: Money,
    val status: String, val busy: Boolean, val needsAttention: Boolean, val typeId: String = "", val districtId: String = "",
)

/** A tenant decision; each choice says in plain words what it costs or changes. */
data class DecisionUi(val id: Long, val propertyId: Long, val text: String, val choices: List<String>, val details: List<String> = emptyList(), val kind: String = "")

data class EstatesUi(
    val unlocked: Boolean,
    val unlockChapter: Int,
    val value: Money,
    val debt: Money,
    val equity: Money,
    val rentPerDay: Money,
    val cash: Money,
    val cities: List<CityPinUi>,
    val properties: List<PropertyRowUi>,
    val decisions: List<DecisionUi>,
)

data class DistrictRowUi(val id: String, val name: String, val cycle: String, val change24h: Double, val yieldPerDay: Double, val cbd: Boolean, val spark: List<Float>, val tier: String)

data class CityUi(val id: String, val name: String, val description: String, val unlocked: Boolean, val chapter: Int, val districts: List<DistrictRowUi>)

data class ListingUi(
    val id: String, val type: String, val icon: String, val size: Double, val condition: Int, val distressed: Boolean, val discount: Double,
    val price: Money, val rentPerDay: Money, val cashNeeded: Map<Double, Money>, val typeId: String = "", val districtId: String = "",
)

data class DistrictUi(
    val id: String, val name: String, val cityName: String, val cycle: String, val change24h: Double, val yieldPerDay: Double, val tier: String,
    val cbd: Boolean, val day: List<Float>, val week: List<Float>, val listings: List<ListingUi>, val owned: List<PropertyRowUi>,
    val unlocked: Boolean, val lockedReason: String?, val ltvOptions: List<Double>, val mortgagesUnlocked: Boolean, val cash: Money,
    val refreshInMinutes: Long,
)

data class TowerOptionUi(val id: String, val name: String, val floors: Int, val totalCost: Money, val nextStage: String?, val nextCost: Money?, val stagesDone: Int)

data class PropertyUi(
    val id: Long, val title: String, val location: String, val icon: String, val typeId: String = "", val districtId: String = "", val value: Money, val costBasis: Money, val gain: Money,
    val mortgage: Money, val condition: Int, val renovations: Int, val maxRenovations: Int, val rentPct: Int, val rentLevels: List<Int>,
    val rentLevelPreview: Map<Int, Money>, val expectedRent6h: Money, val tenantStatus: String, val busyLabel: String?,
    val busyEndsAt: Long?, val simNow: Long, val renovationCost: Money?, val renovationHours: Int, val repairCost: Money?,
    val saleQuoteNet: Money, val saleHours: Int, val selling: Boolean, val insured: Boolean, val insurancePerDay: Money,
    val hostedBusiness: String?, val hostOptions: List<Pair<String, String>>, val tower: TowerOptionUi?, val towerDesigns: List<TowerOptionUi>,
    val towerLocked: String?, val decisions: List<DecisionUi>, val cash: Money,
)

/** Maps engine state to the Estates UI. */
class EstatesMapper(private val engine: GameEngine) {
    private val est = engine.estate
    private val c = engine.content.estates

    private fun title(p: PropertyState) = c.typeById.getValue(p.type).name
    private fun location(p: PropertyState) = c.districtById.getValue(p.district).let { "${it.name}, ${c.cityById.getValue(it.city).name}" }

    fun toUi(s: GameState): EstatesUi {
        val props = s.estate.properties.values.sortedBy { it.id }
        val value = est.portfolioValue(s)
        val debt = est.debt(s)
        val rent = props.fold(Money.ZERO) { a, p -> a + est.expectedRent6h(s, p) } * 4L
        return EstatesUi(
            unlocked = est.isUnlocked(s, "estates"),
            unlockChapter = est.unlockChapter("estates"),
            value = value, debt = debt, equity = value - debt, rentPerDay = rent, cash = s.cash,
            cities = c.cities.map { city ->
                CityPinUi(city.id, city.name, city.x.toFloat(), city.y.toFloat(), est.cityUnlocked(s, city.id), city.chapter,
                    c.districts.count { it.city == city.id }, props.count { c.districtById.getValue(it.district).city == city.id })
            },
            properties = props.map { row(s, it) },
            decisions = decisions(s, null),
        )
    }

    fun row(s: GameState, p: PropertyState): PropertyRowUi {
        val busy = est.busy(s, p.id)
        return PropertyRowUi(
            p.id, title(p), location(p), c.typeById.getValue(p.type).icon, est.propertyValue(s, p), est.expectedRent6h(s, p),
            status(s, p), busy, p.condition < 700 || s.estate.events.any { it.propertyId == p.id }, typeId = p.type, districtId = p.district,
        )
    }

    private fun status(s: GameState, p: PropertyState): String {
        val t = s.timers.firstOrNull { it.targetId == "p${p.id}" }
        return when {
            t?.kind == TimerKind.Sale -> "For sale · ${mins(t.endSim - s.simTimeMs)}"
            t?.kind == TimerKind.Renovation -> (if (t.detailId == "repair") "Repairs" else "Renovating") + " · ${mins(t.endSim - s.simTimeMs)}"
            t?.kind == TimerKind.TowerStage -> "Construction · ${mins(t.endSim - s.simTimeMs)}"
            p.hostedBusiness != null -> "Home of ${engine.content.business(p.hostedBusiness!!).name}"
            p.tenantFrom > s.simTimeMs -> "Finding a tenant · ${mins(p.tenantFrom - s.simTimeMs)}"
            else -> "Rented"
        }
    }

    private fun days(d: Double) = if (d == 1.0) "a day" else "${trim(d)} days"

    private fun trim(d: Double) = if (d == kotlin.math.floor(d)) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)

    fun decisions(s: GameState, propertyId: Long?): List<DecisionUi> = s.estate.events.filter { propertyId == null || it.propertyId == propertyId }.mapNotNull { ev ->
        val p = s.estate.properties[ev.propertyId] ?: return@mapNotNull null
        val def = c.eventById.getValue(ev.event)
        val rent = est.marketRent6h(s, p)
        fun cash(x: Double) = com.arnavgames.tycoon.core.money.MoneyFormat.compact(rent * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(x))
        val details = def.choices.map { ch ->
            buildList {
                if (ch.costRent > 0) add("Costs ${cash(ch.costRent)}")
                if (ch.gainRent > 0) add("+${cash(ch.gainRent)}")
                if (ch.condition != 0) add("Condition ${if (ch.condition > 0) "+" else "−"}${kotlin.math.abs(ch.condition) / 10}%")
                if (ch.vacancyHours > 0) add("Tenant leaves · empty for about ${ch.vacancyHours} h")
                if (ch.boostDays > 0) add("Rent +10% for ${days(ch.boostDays)}")
                if (ch.boostDays < 0) add("Rent −10% for ${days(-ch.boostDays)}")
                if (isEmpty()) add("Rent goes back to normal")
            }.joinToString(" · ")
        }
        DecisionUi(ev.id, ev.propertyId, def.text.replace("{type}", title(p).lowercase()).replace("{district}", c.districtById.getValue(p.district).name), def.choices.map { it.label }, details, kind = def.id)
    }

    private fun indexSeries(s: GameState, districtId: String, hours: Int, points: Int): List<Float> =
        (0..points).map { i -> est.priceIndex(s, districtId, s.simTimeMs - hours * 3_600_000L + i.toLong() * hours * 3_600_000L / points).toFloat() }

    private fun tierWord(t: Double) = when { t >= 2.4 -> "Prime"; t >= 1.5 -> "Upscale"; t >= 1.0 -> "Mid-market"; else -> "Affordable" }

    fun city(s: GameState, id: String): CityUi? {
        val city = c.cityById[id] ?: return null
        return CityUi(city.id, city.name, city.description, est.cityUnlocked(s, id), city.chapter, c.districts.filter { it.city == id }.map { d ->
            val series = indexSeries(s, d.id, 24, 24)
            DistrictRowUi(d.id, d.name, est.cycleWord(s, d.id), series.last() / series.first() - 1.0, d.yieldPerDay, d.cbd, series, tierWord(d.tier))
        })
    }

    fun district(s: GameState, id: String): DistrictUi? {
        val d = c.districtById[id] ?: return null
        val city = c.cityById.getValue(d.city)
        val day = indexSeries(s, id, 24, 48)
        val unlocked = est.cityUnlocked(s, d.city)
        return DistrictUi(
            id = id, name = d.name, cityName = city.name, cycle = est.cycleWord(s, id), change24h = day.last() / day.first() - 1.0,
            yieldPerDay = d.yieldPerDay, tier = tierWord(d.tier), cbd = d.cbd, day = day, week = indexSeries(s, id, 168, 56),
            listings = est.listings(s, id).map { l ->
                val t = c.typeById.getValue(l.type)
                val probe = PropertyState(0, id, l.type, l.size, l.condition, costBasis = l.price)
                ListingUi(l.id, t.name, t.icon, l.size, l.condition, l.distressed, l.discount, l.price,
                    est.marketRent6h(s, probe) * Multiplier.ofDouble(est.rentFactor(100) * 4),
                    est.ltvOptions(s).associateWith { est.buyCost(l, it).first }, typeId = l.type, districtId = l.district)
            },
            owned = s.estate.properties.values.filter { it.district == id }.sortedBy { it.id }.map { row(s, it) },
            unlocked = unlocked,
            lockedReason = when {
                !est.isUnlocked(s, "estates") -> "Estates open in chapter ${est.unlockChapter("estates")}"
                !unlocked -> "${city.name} opens in chapter ${city.chapter}"
                else -> null
            },
            ltvOptions = est.ltvOptions(s), mortgagesUnlocked = est.isUnlocked(s, "mortgages"), cash = s.cash,
            refreshInMinutes = ((est.epoch(s.simTimeMs) + 1) * c.config.listingRefreshHours * 3_600_000L - s.simTimeMs) / 60_000,
        )
    }

    fun property(s: GameState, id: Long): PropertyUi? {
        val p = s.estate.properties[id] ?: return null
        val t = c.typeById.getValue(p.type)
        val d = c.districtById.getValue(p.district)
        val value = est.propertyValue(s, p)
        val timer = s.timers.firstOrNull { it.targetId == "p$id" }
        val busy = timer != null
        val quote = est.saleQuote(s, p, engine.content.markets.config.capitalGainsTax)
        val tw = p.tower?.let { towerUi(s, p, it.design) }
        val designs = if (p.type == "land" && d.cbd && p.tower == null) c.towers.map { towerUi(s, p, it.id) } else emptyList()
        return PropertyUi(
            id = id, title = title(p), location = location(p), icon = t.icon, typeId = p.type, districtId = p.district, value = value, costBasis = p.costBasis, gain = value - p.costBasis,
            mortgage = p.mortgage, condition = p.condition, renovations = p.renovations, maxRenovations = c.config.maxRenovations,
            rentPct = p.rentPct, rentLevels = est.RENT_LEVELS,
            rentLevelPreview = est.RENT_LEVELS.associateWith { lvl -> est.marketRent6h(s, p) * Multiplier.ofDouble(est.rentFactor(lvl) * 4) },
            expectedRent6h = est.expectedRent6h(s, p), tenantStatus = status(s, p),
            busyLabel = timer?.let { when (it.kind) { TimerKind.Sale -> "For sale"; TimerKind.TowerStage -> "Under construction"; else -> if (it.detailId == "repair") "Repairs under way" else "Renovation under way" } },
            busyEndsAt = timer?.endSim, simNow = s.simTimeMs,
            renovationCost = if (p.renovations < c.config.maxRenovations && !busy) est.renovationCost(s, p, repair = false) else null,
            renovationHours = t.renovationHours,
            repairCost = if (p.condition < 1000 && !busy) est.renovationCost(s, p, repair = true) else null,
            saleQuoteNet = quote.net, saleHours = t.saleHours, selling = timer?.kind == TimerKind.Sale,
            insured = p.insured, insurancePerDay = value * Multiplier.ofDouble(c.config.insurancePerDay),
            hostedBusiness = p.hostedBusiness?.let { engine.content.business(it).name },
            hostOptions = t.hosts.filter { it in s.businesses }.map { it to engine.content.business(it).name },
            tower = tw, towerDesigns = designs,
            towerLocked = if (p.type == "land" && d.cbd && !est.isUnlocked(s, "skyscrapers")) "Skyscrapers open in chapter ${est.unlockChapter("skyscrapers")}" else null,
            decisions = decisions(s, id), cash = s.cash,
        )
    }

    private fun towerUi(s: GameState, p: PropertyState, designId: String): TowerOptionUi {
        val design = c.towerById.getValue(designId)
        val done = p.tower?.stagesDone ?: 0
        val total = (0 until design.stageCosts.size).fold(Money.ZERO) { a, i -> a + est.towerStageCost(s, p, designId, i) }
        val next = if (done < design.stageCosts.size) done else null
        return TowerOptionUi(design.id, design.name, design.floors, total, next?.let { design.stageNames[it] }, next?.let { est.towerStageCost(s, p, designId, it) }, done)
    }

    companion object {
        fun mins(ms: Long): String {
            val m = (ms / 60_000).coerceAtLeast(0)
            return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
        }
    }
}
