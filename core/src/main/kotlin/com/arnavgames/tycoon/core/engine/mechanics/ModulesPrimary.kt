package com.arnavgames.tycoon.core.engine.mechanics

import com.arnavgames.tycoon.core.content.ManagerPolicy
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.MechanicState
import kotlin.math.exp

// Primary industries: Farmland, Factory, Fashion House, Mining, Energy.

private fun ms(kind: String) = MechanicState.Module(kind)
private fun Long.milli() = this / 1000.0

/**
 * FARMLAND: seasons and crops (GDD §5.2). Each season (default 180 min) has seeded weather and a seeded spot price.
 * Crops trade yield for weather sensitivity and price swings. A forward contract, decided during the previous season,
 * locks the price at par minus a fee. Crop changes take effect next season, so you plan ahead from the forecast.
 */
object FarmlandModule : MechanicModule {
    override val kind = "farmland"
    override val paramKeys = setOf("seasonMinutes", "contractFee")

    data class Crop(val id: String, val name: String, val yield: Double, val weatherSensitivity: Double, val priceVolatility: Double)

    val crops = listOf(
        Crop("wheat", "Wheat", 1.00, 0.20, 0.12),
        Crop("coffee", "Coffee", 1.25, 0.45, 0.28),
        Crop("cocoa", "Cocoa", 1.45, 0.65, 0.42),
    )

    private fun season(def: MechanicDef.Module, minute: Long) = minute / def.p("seasonMinutes", 180.0).toLong()

    /** Weather multiplier for a season (seeded, so the forecast can preview the next one). */
    fun weather(ctx: MechCtx, season: Long): Double {
        val g = ctx.rng("weather", season).nextGaussian()
        return (1.0 + 0.25 * g).coerceIn(0.5, 1.5)
    }

    private fun price(ctx: MechCtx, season: Long, crop: Crop): Double {
        val g = ctx.rng("price:${crop.id}", season).nextGaussian()
        return (1.0 + crop.priceVolatility * g).coerceIn(0.45, 1.9)
    }

    private fun crop(id: String) = crops.firstOrNull { it.id == id } ?: crops.first()

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind)
        .withS("crop" to "wheat", "nextCrop" to "wheat")
        .with("season" to season(def, ctx.minute), "contract" to 0, "nextContract" to 0)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val c = crop(s.s("crop"))
        val season = s.n("season")
        val w = weather(ctx, season)
        val p = if (s.n("contract") == 1L) 1.0 - def.p("contractFee", 0.03) else price(ctx, season, c)
        return c.yield * (1 + c.weatherSensitivity * (w - 1)) * p
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val now = season(def, ctx.minute)
        if (now == s.n("season")) return s
        // New season: plant the planned crop and apply the planned contract.
        return s.withS("crop" to s.s("nextCrop", s.s("crop"))).with("season" to now, "contract" to s.n("nextContract"), "nextContract" to 0)
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "crop" -> if (crops.any { it.id == value }) Result.success(ActResult(s.withS("nextCrop" to value))) else invalid("unknown crop")
        "contract" -> Result.success(ActResult(s.with("nextContract" to if (value == "on") 1 else 0)))
        else -> invalid("unknown action")
    }

    /** Forecast for next season: the true weather blurred by a seeded error (about 70% reliable). */
    fun forecast(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val next = s.n("season") + 1
        val noise = ctx.rng("forecast", next).nextGaussian() * 0.15
        return (weather(ctx, next) + noise).coerceIn(0.5, 1.5)
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val f = forecast(def, s, ctx)
        val best = crops.maxBy { c -> c.yield * (1 + c.weatherSensitivity * (f - 1)) * if (policy == ManagerPolicy.Safe) (1 - c.priceVolatility * 0.5) else 1.0 }
        val pick = if (skill >= 0.8) best else crops.first()
        val contract = when (policy) {
            ManagerPolicy.Safe -> 1L
            ManagerPolicy.Balanced -> if (pick.priceVolatility > 0.25) 1L else 0L
            ManagerPolicy.Aggressive -> 0L
        }
        return ActResult(s.withS("nextCrop" to pick.id).with("nextContract" to contract))
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val c = crop(s.s("crop"))
        val seasonLen = def.p("seasonMinutes", 180.0).toLong()
        val left = seasonLen - ctx.minute % seasonLen
        val w = weather(ctx, s.n("season"))
        val f = forecast(def, s, ctx)
        fun label(x: Double) = when { x >= 1.15 -> "Excellent"; x >= 1.0 -> "Good"; x >= 0.85 -> "Fair"; else -> "Poor" }
        return MechanicView(
            title = "Seasons & crops",
            summary = "Pick next season's crop from the forecast. Riskier crops yield more but swing with weather and prices. A forward contract locks the price.",
            stats = listOf(
                ViewStat("Planted", c.name),
                ViewStat("Weather now", label(w), if (w >= 1) Tone.Good else Tone.Warn),
                ViewStat("Next season forecast", "${label(f)} (≈70% reliable)"),
                ViewStat("Harvest in", "${left} min"),
                ViewStat("Price", if (s.n("contract") == 1L) "Contract (fixed)" else "Spot market"),
            ),
            controls = listOf(
                ViewControl.Choice("crop", "Next season's crop", "Takes effect when the season turns.", crops.map { ViewOption(it.id, it.name, "Yield ×${it.yield} · weather risk ${pct(it.weatherSensitivity)} · price swing ±${pct(it.priceVolatility)}") }, s.s("nextCrop")),
                ViewControl.Choice("contract", "Forward contract for next season", "Locks the price at par minus ${pct(def.p("contractFee", 0.03))}.", listOf(ViewOption("off", "Sell at spot", "Exposed to the price swing"), ViewOption("on", "Lock contract", "Steady, small fee")), if (s.n("nextContract") == 1L) "on" else "off"),
            ),
        )
    }
}

/**
 * FACTORY: production lines. Output follows the slowest of three stages, so upgrading anything but the bottleneck
 * is wasted. Owning Mining (inputs at cost) or Logistics (cheaper freight) lowers upkeep.
 */
object FactoryModule : MechanicModule {
    override val kind = "factory"
    override val paramKeys = setOf("perStage", "upgradeSeconds", "upgradeGrowth")
    val stages = listOf("fabrication" to "Fabrication", "assembly" to "Assembly", "packaging" to "Packaging")

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with(*stages.map { it.first to 0L }.toTypedArray())

    private fun minStage(s: MechanicState.Module) = stages.minOf { s.n(it.first) }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) = 0.55 + def.p("perStage", 0.15) * minStage(s)

    override fun upkeepFactor(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double =
        (if (ctx.owns("mining")) 0.8 else 1.0) * (if (ctx.owns("logistics")) 0.9 else 1.0)

    fun upgradeCost(def: MechanicDef.Module, level: Long, ctx: MechCtx): Money =
        ctx.cost(def.p("upgradeSeconds", 90.0) * Math.pow(def.p("upgradeGrowth", 1.7), level.toDouble()))

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> {
        if (action != "upgrade" || stages.none { it.first == value }) return invalid("unknown stage")
        val lv = s.n(value)
        if (lv >= 12) return invalid("This stage is fully upgraded")
        return Result.success(ActResult(s.with(value to lv + 1), upgradeCost(def, lv, ctx)))
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val bottleneck = stages.minBy { s.n(it.first) }.first
        val cost = upgradeCost(def, s.n(bottleneck), ctx)
        return if (cost <= budget && s.n(bottleneck) < 12) ActResult(s.with(bottleneck to s.n(bottleneck) + 1), cost) else ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val bottleneck = stages.minBy { s.n(it.first) }
        return MechanicView(
            title = "Production lines",
            summary = "Output follows the slowest stage. Upgrade the bottleneck; upgrading anything else is wasted until the others catch up.",
            stats = listOf(
                ViewStat("Bottleneck", bottleneck.second, Tone.Warn),
                ViewStat("Line efficiency", pct(efficiency(def, s, ctx))),
                ViewStat("Input costs", if (ctx.owns("mining")) "Own ore at cost" else "Market price", if (ctx.owns("mining")) Tone.Good else Tone.Neutral),
            ),
            controls = stages.map { (id, name) ->
                val lv = s.n(id)
                ViewControl.Action("upgrade", "$name · level $lv", if (id == bottleneck.first) "Bottleneck: upgrading this raises output." else "Not the bottleneck right now.", "Upgrade", if (lv >= 12) null else upgradeCost(def, lv, ctx), lv < 12, id)
            },
        )
    }
}

/**
 * FASHION HOUSE: trend calls. Each season (default 6 h) two of five trends are secretly hot. You choose two for the
 * collection during the first hour; hits sell at a premium. The news offers a hint (more reliable with Media).
 * Owning Retail sends misses to the outlet, softening flops.
 */
object FashionModule : MechanicModule {
    override val kind = "fashion"
    override val paramKeys = setOf("seasonMinutes", "lockMinutes")
    val trends = listOf("quiet_luxury" to "Quiet Luxury", "neon_revival" to "Neon Revival", "coastal_linen" to "Coastal Linen", "tailored_street" to "Tailored Street", "heritage_knit" to "Heritage Knit")

    private fun season(def: MechanicDef.Module, minute: Long) = minute / def.p("seasonMinutes", 360.0).toLong()

    fun hot(ctx: MechCtx, season: Long): Set<String> {
        val r = ctx.rng("trends", season)
        return r.shuffled(trends.map { it.first }).take(2).toSet()
    }

    fun hint(ctx: MechCtx, season: Long): String {
        val r = ctx.rng("hint", season)
        val reliable = r.chance(if (ctx.owns("media")) 0.9 else 0.7)
        val hotList = hot(ctx, season).sorted()
        return if (reliable) hotList[r.nextInt(2)] else r.pick(trends.map { it.first }.filter { it !in hotList })
    }

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).withS("pick1" to trends[0].first, "pick2" to trends[1].first).with("season" to season(def, ctx.minute))

    private fun hits(s: MechanicState.Module, ctx: MechCtx): Int {
        val h = hot(ctx, s.n("season"))
        return listOf(s.s("pick1"), s.s("pick2")).distinct().count { it in h }
    }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val e = 0.6 + 0.45 * hits(s, ctx)
        return if (ctx.owns("retail")) maxOf(e, 0.75) else e
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val now = season(def, ctx.minute)
        return if (now != s.n("season")) s.with("season" to now) else s
    }

    private fun locked(def: MechanicDef.Module, ctx: MechCtx) = ctx.minute % def.p("seasonMinutes", 360.0).toLong() >= def.p("lockMinutes", 60.0).toLong()

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> {
        if (action != "pick" || trends.none { it.first == value }) return invalid("unknown trend")
        if (locked(def, ctx)) return invalid("This season's collection is already in stores")
        if (value == s.s("pick1") || value == s.s("pick2")) return Result.success(ActResult(s))
        // Replace the older pick: the new trend becomes pick2, the previous pick2 moves to pick1.
        return Result.success(ActResult(s.withS("pick1" to s.s("pick2"), "pick2" to value)))
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        if (locked(def, ctx)) return ActResult(s)
        val h = hint(ctx, s.n("season"))
        val other = if (skill >= 0.9) hot(ctx, s.n("season")).firstOrNull { it != h } ?: trends.first { it.first != h }.first
        else trends.first { it.first != h }.first
        return ActResult(s.withS("pick1" to other, "pick2" to h))
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val len = def.p("seasonMinutes", 360.0).toLong()
        val into = ctx.minute % len
        val h = hint(ctx, s.n("season"))
        val name = { id: String -> trends.first { it.first == id }.second }
        return MechanicView(
            title = "Trend calls",
            summary = "Two of five trends are hot each season. Choose two for the collection before it ships. The news hints at one.",
            stats = listOf(
                ViewStat("Collection", "${name(s.s("pick1"))} + ${name(s.s("pick2"))}"),
                ViewStat("Trade press hint", "“${name(h)}” is turning heads (${if (ctx.owns("media")) "90" else "70"}% reliable)"),
                ViewStat(if (locked(def, ctx)) "Season ends in" else "Collection ships in", if (locked(def, ctx)) "${len - into} min" else "${def.p("lockMinutes", 60.0).toLong() - into} min"),
                ViewStat("Sell-through", when (hits(s, ctx)) { 2 -> "Sell-out"; 1 -> "Steady"; else -> "Slow" }, when (hits(s, ctx)) { 2 -> Tone.Good; 1 -> Tone.Neutral; else -> Tone.Bad }),
            ),
            controls = listOf(
                ViewControl.Choice("pick", "Add a trend", "The newest pick replaces the older one.", trends.map { ViewOption(it.first, it.second, if (it.first == s.s("pick1") || it.first == s.s("pick2")) "In the collection" else "") }, s.s("pick2"), enabled = !locked(def, ctx)),
            ),
        )
    }
}

/**
 * MINING: survey and extract. A deposit's grade sets output; it depletes over ~2 days. Surveying reveals three
 * candidate claims before you commit; a blind claim is a gamble. Deep mining yields more but risks accidents.
 */
object MiningModule : MechanicModule {
    override val kind = "mining"
    override val paramKeys = setOf("depletionMinutes", "surveySeconds", "claimSeconds")
    private val depths = listOf(Triple("shallow", "Shallow", 0.85), Triple("standard", "Standard", 1.0), Triple("deep", "Deep", 1.3))

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).withS("depth" to "standard").with("grade" to 1000, "remaining" to 1000, "claims" to 0)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val depth = depths.first { it.first == s.s("depth", "standard") }.third
        val accident = if (s.n("accidentUntil") > ctx.minute) 0.3 else 1.0
        return s.n("grade").milli() * depth * (0.3 + 0.7 * s.n("remaining").milli()) * accident
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val deep = s.s("depth") == "deep"
        val perMinute = 1000.0 / def.p("depletionMinutes", 2880.0) * if (deep) 1.3 else 1.0
        val acc = (s.n("acc") + (perMinute * 1000).toLong())
        var ns = s.with("remaining" to (s.n("remaining") - acc / 1000).coerceAtLeast(0), "acc" to acc % 1000)
        val risk = when (s.s("depth")) { "deep" -> 0.0008; "standard" -> 0.0002; else -> 0.0 }
        if (s.n("accidentUntil") <= ctx.minute && risk > 0 && ctx.rng("accident", ctx.minute).chance(risk)) ns = ns.with("accidentUntil" to ctx.minute + 60)
        return ns
    }

    private fun rollGrade(ctx: MechCtx, claim: Long, slot: Int) = (600 + ctx.rng("grade:$slot", claim).nextInt(1001)).toLong()

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "depth" -> if (depths.any { it.first == value }) Result.success(ActResult(s.withS("depth" to value))) else invalid("unknown depth")
        "survey" -> {
            if (s.n("surveyed") == 1L) invalid("Already surveyed")
            else Result.success(ActResult(s.with("surveyed" to 1), ctx.cost(def.p("surveySeconds", 150.0))))
        }
        "claim" -> {
            val claim = s.n("claims")
            val slot = value.toIntOrNull()?.takeIf { s.n("surveyed") == 1L && it in 0..2 } ?: 3 // 3 = blind
            val grade = rollGrade(ctx, claim, slot)
            Result.success(ActResult(s.with("grade" to grade, "remaining" to 1000, "claims" to claim + 1, "surveyed" to 0, "acc" to 0), ctx.cost(def.p("claimSeconds", 300.0))))
        }
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        var ns = s.withS("depth" to when (policy) { ManagerPolicy.Safe -> "shallow"; ManagerPolicy.Balanced -> "standard"; ManagerPolicy.Aggressive -> "deep" })
        var spent = Money.ZERO
        if (ns.n("remaining") < 200) {
            val survey = ctx.cost(def.p("surveySeconds", 150.0))
            val claim = ctx.cost(def.p("claimSeconds", 300.0))
            if (survey + claim <= budget) {
                val best = (0..2).maxBy { rollGrade(ctx, ns.n("claims"), it) }
                val pick = if (skill >= 0.85) best else 0
                ns = ns.with("grade" to rollGrade(ctx, ns.n("claims"), pick), "remaining" to 1000, "claims" to ns.n("claims") + 1, "surveyed" to 0, "acc" to 0)
                spent = survey + claim
            }
        }
        return ActResult(ns, spent)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val claim = s.n("claims")
        val controls = mutableListOf<ViewControl>(
            ViewControl.Choice("depth", "Mining depth", "Deeper yields more ore but risks costly accidents.", depths.map { ViewOption(it.first, it.second, "Output ×${it.third}" + if (it.first == "deep") " · accident risk" else if (it.first == "standard") " · low risk" else " · safe") }, s.s("depth")),
            ViewControl.Progress("Deposit remaining", s.n("remaining").milli(), "Grade ${"%.2f".format(s.n("grade").milli())} · ${pct(s.n("remaining").milli())} left"),
        )
        if (s.n("surveyed") == 1L) {
            (0..2).forEach { i ->
                controls += ViewControl.Action("claim", "Candidate claim ${i + 1}", "Surveyed grade ${"%.2f".format(rollGrade(ctx, claim, i).milli())}", "Open claim", ctx.cost(def.p("claimSeconds", 300.0)), value = i.toString())
            }
        } else {
            controls += ViewControl.Action("survey", "Geological survey", "Reveals three candidate claims before you commit.", "Survey", ctx.cost(def.p("surveySeconds", 150.0)))
            controls += ViewControl.Action("claim", "Blind claim", "Open a new deposit without surveying. Could be rich, could be poor.", "Open blind", ctx.cost(def.p("claimSeconds", 300.0)), value = "blind")
        }
        return MechanicView(
            title = "Survey & extract",
            summary = "Ore grade sets output, and every deposit runs dry. Survey before you claim, and choose how deep to dig.",
            stats = listOf(
                ViewStat("Ore grade", "%.2f".format(s.n("grade").milli()), if (s.n("grade") >= 1100) Tone.Good else if (s.n("grade") < 850) Tone.Bad else Tone.Neutral),
                ViewStat("Status", if (s.n("accidentUntil") > ctx.minute) "Accident: shafts closed ${s.n("accidentUntil") - ctx.minute} min" else "Operating", if (s.n("accidentUntil") > ctx.minute) Tone.Bad else Tone.Good),
            ),
            controls = controls,
        )
    }
}

/**
 * ENERGY: wells and contracts. Output declines after drilling (redrill to restore). Sell at a fluctuating spot price
 * or contract a share at a fixed price — the hedge decision. Owning Energy cuts upkeep for Factory, Telecom and Hotels.
 */
object EnergyModule : MechanicModule {
    override val kind = "energy"
    override val paramKeys = setOf("declineMinutes", "redrillSeconds")

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("drilledAt" to ctx.minute, "share" to 50)

    fun spot(ctx: MechCtx): Double {
        val hour = ctx.minute / 60
        val wave = 0.28 * kotlin.math.sin(2 * Math.PI * hour / 30.0)
        val noise = 0.08 * ctx.rng("spot", hour).nextGaussian()
        return (1.0 + wave + noise).coerceIn(0.55, 1.6)
    }

    fun production(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val age = (ctx.minute - s.n("drilledAt")).coerceAtLeast(0)
        return 0.45 + 1.05 * exp(-age / def.p("declineMinutes", 4320.0))
    }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val share = s.n("share") / 100.0
        return production(def, s, ctx) * (share * 1.0 + (1 - share) * spot(ctx))
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "share" -> value.toLongOrNull()?.takeIf { it in listOf(0L, 25L, 50L, 75L, 100L) }?.let { Result.success(ActResult(s.with("share" to it))) } ?: invalid("share")
        "redrill" -> Result.success(ActResult(s.with("drilledAt" to ctx.minute), ctx.cost(def.p("redrillSeconds", 600.0))))
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        var ns = s.with("share" to when (policy) { ManagerPolicy.Safe -> 75L; ManagerPolicy.Balanced -> 50L; ManagerPolicy.Aggressive -> 0L })
        val cost = ctx.cost(def.p("redrillSeconds", 600.0))
        return if (production(def, ns, ctx) < 0.7 && cost <= budget) ActResult(ns.with("drilledAt" to ctx.minute), cost) else ActResult(ns)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val sp = spot(ctx)
        val prod = production(def, s, ctx)
        return MechanicView(
            title = "Wells & contracts",
            summary = "Wells decline after drilling. Sell at the spot price or lock part of the output into a fixed contract.",
            stats = listOf(
                ViewStat("Well output", pct(prod / 1.5), if (prod < 0.8) Tone.Warn else Tone.Good),
                ViewStat("Spot price", "%.2f× par".format(sp), if (sp >= 1) Tone.Good else Tone.Bad),
                ViewStat("Contracted", "${s.n("share")}%"),
            ),
            controls = listOf(
                ViewControl.Choice("share", "Contract share", "Contracted output earns par regardless of the spot price.", listOf(0L, 25L, 50L, 75L, 100L).map { ViewOption(it.toString(), "$it%", if (it == 0L) "All spot" else if (it == 100L) "Fully hedged" else "") }, s.n("share").toString()),
                ViewControl.Action("redrill", "Redrill wells", "Restores output to full.", "Redrill", ctx.cost(def.p("redrillSeconds", 600.0))),
            ),
        )
    }
}

