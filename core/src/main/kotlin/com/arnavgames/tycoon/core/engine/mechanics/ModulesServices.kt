package com.arnavgames.tycoon.core.engine.mechanics

import com.arnavgames.tycoon.core.content.ManagerPolicy
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.MechanicState
import kotlin.math.exp
import kotlin.math.pow

// Services: Hotels, Bank, Construction, Media, Tech.

private fun ms(kind: String) = MechanicState.Module(kind)
private fun Long.milli() = this / 1000.0
private val rates = (70..160 step 10).toList()

/** Weekly tourism wave, constant within each hour. */
internal fun tourism(ctx: MechCtx): Double {
    val hour = ctx.minute / 60
    return 1.0 + 0.25 * kotlin.math.sin(2 * Math.PI * hour / (24.0 * 7))
}

/**
 * HOTELS: occupancy and room rates. Demand follows a weekly tourism wave; your rate trades occupancy for revenue.
 * Star upgrades raise what guests will pay. Owning Airline brings more guests; owning Sports fills rooms on matchdays.
 */
object HotelsModule : MechanicModule {
    override val kind = "hotels"
    override val paramKeys = setOf("elasticity", "starSeconds")

    private fun demandBoost(ctx: MechCtx) = (if (ctx.owns("airline")) 1.15 else 1.0) * (if (ctx.owns("sports")) 1.05 else 1.0)

    fun occupancy(def: MechanicDef.Module, rate: Int, ctx: MechCtx): Double =
        (0.8 * tourism(ctx) * demandBoost(ctx) * (100.0 / rate).pow(def.p("elasticity", 1.4))).coerceAtMost(1.0)

    private fun value(def: MechanicDef.Module, rate: Int, stars: Long, ctx: MechCtx) = occupancy(def, rate, ctx) * rate / 100.0 * (0.8 + 0.1 * stars)

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("rate" to 100, "stars" to 2)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) = value(def, s.n("rate").toInt(), s.n("stars"), ctx) / 0.8 * 1.0

    fun bestRate(def: MechanicDef.Module, stars: Long, ctx: MechCtx) = rates.maxBy { value(def, it, stars, ctx) }

    private fun starCost(def: MechanicDef.Module, stars: Long, ctx: MechCtx): Money = ctx.cost(def.p("starSeconds", 300.0) * 2.0.pow(stars.toDouble()))

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "rate" -> value.toLongOrNull()?.takeIf { it.toInt() in rates }?.let { Result.success(ActResult(s.with("rate" to it))) } ?: invalid("rate")
        "stars" -> if (s.n("stars") >= 5) invalid("Already five stars") else Result.success(ActResult(s.with("stars" to s.n("stars") + 1), starCost(def, s.n("stars"), ctx)))
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val best = bestRate(def, s.n("stars"), ctx)
        val rate = (100 + (best - 100) * skill).toInt() / 10 * 10
        return ActResult(s.with("rate" to rate.toLong().coerceIn(70, 160)))
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val occ = occupancy(def, s.n("rate").toInt(), ctx)
        return MechanicView(
            title = "Occupancy & rates",
            summary = "Tourism rises and falls through the week. Price rooms for the season; stars raise what guests will pay.",
            stats = listOf(
                ViewStat("Occupancy", pct(occ), if (occ > 0.8) Tone.Good else if (occ < 0.5) Tone.Warn else Tone.Neutral),
                ViewStat("Tourism", if (tourism(ctx) >= 1) "High season" else "Low season"),
                ViewStat("Rating", "${s.n("stars")} stars"),
                ViewStat("Guests from your airline", if (ctx.owns("airline")) "+15%" else "—", if (ctx.owns("airline")) Tone.Good else Tone.Neutral),
            ),
            controls = listOf(
                ViewControl.Choice("rate", "Room rate", "Percent of the standard rate.", rates.map { ViewOption(it.toString(), "$it%", "Occupancy ${pct(occupancy(def, it, ctx))}") }, s.n("rate").toString()),
                ViewControl.Action("stars", "Upgrade to ${s.n("stars") + 1} stars", "Guests pay more at higher ratings.", "Upgrade", if (s.n("stars") >= 5) null else starCost(def, s.n("stars"), ctx), s.n("stars") < 5),
            ),
        )
    }
}

/**
 * BANK: the balance sheet. Your lending standard trades yield for defaults; a thin reserve lends more but risks a
 * bank run. Standard lending with a 20% reserve is par.
 */
object BankModule : MechanicModule {
    override val kind = "bank"
    override val paramKeys = setOf("runChance")
    private val standards = listOf(Triple("prime", "Prime", 0.85 to 0.02), Triple("standard", "Standard", 1.0 to 0.05), Triple("subprime", "Subprime", 1.3 to 0.12))
    private val reserves = listOf(10L, 20L, 30L)
    private const val PAR = 0.8 * 1.0 * 0.95

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).withS("standard" to "standard").with("reserve" to 20)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val (yieldF, defaults) = standards.first { it.first == s.s("standard", "standard") }.third
        val lend = 1.0 - s.n("reserve") / 100.0
        val run = if (s.n("runUntil") > ctx.minute) 0.3 else 1.0
        return lend * yieldF * (1 - defaults) / PAR * run
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        if (s.n("reserve") > 10 || s.n("runUntil") > ctx.minute) return s
        val chance = def.p("runChance", 0.0006) * if (s.s("standard") == "subprime") 2 else 1
        return if (ctx.rng("run", ctx.minute).chance(chance)) s.with("runUntil" to ctx.minute + 120) else s
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "standard" -> if (standards.any { it.first == value }) Result.success(ActResult(s.withS("standard" to value))) else invalid("standard")
        "reserve" -> value.toLongOrNull()?.takeIf { it in reserves }?.let { Result.success(ActResult(s.with("reserve" to it))) } ?: invalid("reserve")
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money) = ActResult(
        when (policy) {
            ManagerPolicy.Safe -> s.withS("standard" to "prime").with("reserve" to 30)
            ManagerPolicy.Balanced -> s.withS("standard" to "standard").with("reserve" to 20)
            ManagerPolicy.Aggressive -> s.withS("standard" to "subprime").with("reserve" to 10)
        },
    )

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val (_, defaults) = standards.first { it.first == s.s("standard", "standard") }.third
        return MechanicView(
            title = "Balance sheet",
            summary = "Riskier borrowers pay more but default more. A thin reserve lends more money but can trigger a bank run.",
            stats = listOf(
                ViewStat("Expected defaults", pct(defaults), if (defaults > 0.08) Tone.Warn else Tone.Neutral),
                ViewStat("Status", if (s.n("runUntil") > ctx.minute) "Bank run! ${s.n("runUntil") - ctx.minute} min" else "Stable", if (s.n("runUntil") > ctx.minute) Tone.Bad else Tone.Good),
                ViewStat("Run risk", if (s.n("reserve") <= 10) "Present" else "None", if (s.n("reserve") <= 10) Tone.Warn else Tone.Good),
            ),
            controls = listOf(
                ViewControl.Choice("standard", "Lending standard", "Who you lend to.", standards.map { ViewOption(it.first, it.second, "Yield ×${it.third.first} · defaults ${pct(it.third.second)}") }, s.s("standard")),
                ViewControl.Choice("reserve", "Reserve ratio", "Cash kept back for withdrawals.", reserves.map { ViewOption(it.toString(), "$it%", if (it == 10L) "Lends most · run risk" else if (it == 30L) "Very safe" else "Par") }, s.n("reserve").toString()),
            ),
        )
    }
}

/**
 * CONSTRUCTION: tenders and crews. A new tender appears every two hours. Bid a margin: higher margins win less
 * often. A won project runs for three hours and lifts output with its size and margin.
 */
object ConstructionModule : MechanicModule {
    override val kind = "construction"
    override val paramKeys = setOf("tenderMinutes", "projectMinutes")
    private val margins = listOf(Triple("low", "Lean (win 85%)", 0.85 to 1.15), Triple("fair", "Fair (win 60%)", 0.6 to 1.4), Triple("high", "Rich (win 30%)", 0.3 to 1.75))

    private fun tender(def: MechanicDef.Module, ctx: MechCtx) = ctx.minute / def.p("tenderMinutes", 120.0).toLong()
    fun size(ctx: MechCtx, tender: Long) = (800 + ctx.rng("tender", tender).nextInt(801)).toLong()

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("lastBid" to -1)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double =
        if (s.n("projectUntil") > ctx.minute) 0.55 + 0.6 * s.n("projectSize").milli() * (s.n("projectMargin") / 100.0) else 0.55

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> {
        if (action != "bid") return invalid("unknown action")
        val m = margins.firstOrNull { it.first == value } ?: return invalid("margin")
        val t = tender(def, ctx)
        if (s.n("lastBid") == t) return invalid("You already bid on this tender")
        if (s.n("projectUntil") > ctx.minute) return invalid("Your crews are busy on a project")
        val won = ctx.rng("bid:${m.first}", t).chance(m.third.first)
        val ns = if (won) s.with("projectUntil" to ctx.minute + def.p("projectMinutes", 180.0).toLong(), "projectSize" to size(ctx, t), "projectMargin" to (m.third.second * 100).toLong(), "won" to s.n("won") + 1)
        else s
        return Result.success(ActResult(ns.with("lastBid" to t, "lastWon" to if (won) 1 else 0)))
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val t = tender(def, ctx)
        if (s.n("lastBid") == t || s.n("projectUntil") > ctx.minute) return ActResult(s)
        val margin = when (policy) { ManagerPolicy.Safe -> "low"; ManagerPolicy.Balanced -> "fair"; ManagerPolicy.Aggressive -> "high" }
        return act(def, s, "bid", margin, ctx).getOrNull() ?: ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val t = tender(def, ctx)
        val busy = s.n("projectUntil") > ctx.minute
        val nextIn = def.p("tenderMinutes", 120.0).toLong() - ctx.minute % def.p("tenderMinutes", 120.0).toLong()
        return MechanicView(
            title = "Tenders & crews",
            summary = "Bid for projects against rival builders. Lean bids usually win; rich bids pay more when they do.",
            stats = listOf(
                ViewStat("Crews", if (busy) "On site · ${s.n("projectUntil") - ctx.minute} min left" else "Available", if (busy) Tone.Good else Tone.Neutral),
                ViewStat("Current tender", "Size ${"%.2f".format(size(ctx, t).milli())}" + if (s.n("lastBid") == t) (if (s.n("lastWon") == 1L) " · won" else " · lost") else ""),
                ViewStat("Next tender in", "$nextIn min"),
                ViewStat("Projects won", s.n("won").toString()),
            ),
            controls = listOf(
                ViewControl.Choice("bid", "Submit a bid", if (busy) "Crews are busy." else if (s.n("lastBid") == t) "Wait for the next tender." else "One bid per tender.", margins.map { ViewOption(it.first, it.second, "Output ×${"%.2f".format(0.55 + 0.6 * size(ctx, t).milli() * it.third.second)} if won") }, null, enabled = !busy && s.n("lastBid") != t),
            ),
        )
    }
}

/**
 * MEDIA: audience and slate. Each season brings three show pitches; greenlit shows build audience (hits more likely
 * with higher quality). Campaigns lend your audience to another business for two hours (+15% income).
 */
object MediaModule : MechanicModule {
    override val kind = "media"
    override val paramKeys = setOf("seasonMinutes", "showMinutes", "campaignSeconds")
    private val genres = listOf("Drama", "Reality", "Documentary", "Comedy", "Thriller", "Cooking", "Sport talk")

    private fun season(def: MechanicDef.Module, ctx: MechCtx) = ctx.minute / def.p("seasonMinutes", 480.0).toLong()
    fun pitch(ctx: MechCtx, season: Long, i: Int): Pair<String, Long> {
        val r = ctx.rng("pitch:$i", season)
        return r.pick(genres) to (300 + r.nextInt(701)).toLong()
    }

    private fun pitchCost(quality: Long, ctx: MechCtx) = ctx.cost(200.0 + quality * 0.5)

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind)

    /** Running shows are stored as show<N>Until / show<N>Aud (audience in milli). */
    private fun audience(s: MechanicState.Module, ctx: MechCtx): Double =
        (0 until 4).sumOf { i -> if (s.n("show${i}Until") > ctx.minute) s.n("show${i}Aud").milli() else 0.0 }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) = 0.5 + minOf(1.8, audience(s, ctx) * 0.6)

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "greenlight" -> {
            val i = value.toIntOrNull()?.takeIf { it in 0..2 } ?: return invalid("pitch")
            val season = season(def, ctx)
            if (s.n("lit${i}Season", -1) == season) return invalid("Already greenlit")
            val slot = (0 until 4).firstOrNull { s.n("show${it}Until") <= ctx.minute } ?: return invalid("Your schedule is full")
            val (_, q) = pitch(ctx, season, i)
            val hit = ctx.rng("hit:$i", season).chance(q / 1000.0)
            val aud = (q * if (hit) 1.5 else 0.5).toLong()
            Result.success(ActResult(s.with("show${slot}Until" to ctx.minute + def.p("showMinutes", 960.0).toLong(), "show${slot}Aud" to aud, "lit${i}Season" to season, "hits" to s.n("hits") + if (hit) 1 else 0), pitchCost(q, ctx)))
        }
        "campaign" -> {
            if (value.isBlank() || value == "media") return invalid("Choose another business")
            if (s.n("campaignUntil") > ctx.minute) return invalid("A campaign is already running")
            Result.success(ActResult(s.withS("campaignTarget" to value).with("campaignUntil" to ctx.minute + 120), ctx.cost(def.p("campaignSeconds", 300.0))))
        }
        else -> invalid("unknown action")
    }

    /** Income multiplier media lends to [businessId] (read by the economy for every business). */
    fun campaignBoost(s: MechanicState.Module, businessId: String, minute: Long): Double =
        if (s.s("campaignTarget") == businessId && s.n("campaignUntil") > minute) 1.15 else 1.0

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val season = season(def, ctx)
        val best = (0..2).filter { s.n("lit${it}Season", -1) != season }.maxByOrNull { pitch(ctx, season, it).second } ?: return ActResult(s)
        val cost = pitchCost(pitch(ctx, season, best).second, ctx)
        if (cost > budget || (0 until 4).none { s.n("show${it}Until") <= ctx.minute }) return ActResult(s)
        return act(def, s, "greenlight", best.toString(), ctx).getOrNull() ?: ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val season = season(def, ctx)
        val running = (0 until 4).count { s.n("show${it}Until") > ctx.minute }
        val controls = (0..2).map { i ->
            val (genre, q) = pitch(ctx, season, i)
            val lit = s.n("lit${i}Season", -1) == season
            ViewControl.Action("greenlight", "$genre pitch", "Quality ${q / 10}% · ${if (lit) "greenlit" else "hit chance ${q / 10}%"}", if (lit) "Greenlit" else "Greenlight", if (lit) null else pitchCost(q, ctx), !lit && running < 4, i.toString())
        }.toMutableList<ViewControl>()
        val targets = ctx.owned.filter { it != "media" }.sorted()
        controls += ViewControl.Choice(
            "campaign", "Run a campaign (2 h, +15%)", "Costs ${com.arnavgames.tycoon.core.money.MoneyFormat.short(ctx.cost(def.p("campaignSeconds", 300.0)))}. Boosts one of your businesses.",
            targets.map { ViewOption(it, it.replaceFirstChar { c -> c.uppercase() }, "") }, s.s("campaignTarget").takeIf { s.n("campaignUntil") > ctx.minute }, enabled = s.n("campaignUntil") <= ctx.minute,
        )
        return MechanicView(
            title = "Audience & slate",
            summary = "Greenlight shows to build an audience. Lend that audience to your other businesses with a campaign.",
            stats = listOf(
                ViewStat("Shows on air", "$running / 4"),
                ViewStat("Audience", "%.1f".format(audience(s, ctx))),
                ViewStat("Hits so far", s.n("hits").toString()),
                ViewStat("Campaign", if (s.n("campaignUntil") > ctx.minute) "${s.s("campaignTarget")} · ${s.n("campaignUntil") - ctx.minute} min" else "None"),
            ),
            controls = controls,
        )
    }
}

/**
 * TECH: R&D and launches. Hype builds every minute (faster with a research focus, which slows current sales).
 * Launching turns hype into a product whose quality is part skill, part luck; products ramp up, then fade.
 */
object TechModule : MechanicModule {
    override val kind = "tech"
    override val paramKeys = setOf("hypeMinutes", "lifeMinutes")
    private val focuses = listOf(Triple("ship", "Ship features", 1.05 to 0.5), Triple("balanced", "Balanced", 1.0 to 1.0), Triple("research", "Deep research", 0.85 to 2.0))

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).withS("focus" to "balanced").with("hype" to 0, "launchedAt" to -100_000, "quality" to 0)

    private fun lifecycle(def: MechanicDef.Module, age: Long): Double {
        if (age < 0) return 0.0
        return minOf(age / 60.0, 1.0) * (0.3 + 0.7 * exp(-age / def.p("lifeMinutes", 720.0)))
    }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val f = focuses.first { it.first == s.s("focus", "balanced") }.third.first
        val product = s.n("quality").milli() * lifecycle(def, ctx.minute - s.n("launchedAt"))
        return (0.6 + product) * f
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val speed = focuses.first { it.first == s.s("focus", "balanced") }.third.second
        val gain = (1000.0 / def.p("hypeMinutes", 120.0) * speed * 1000).toLong()
        val acc = s.n("acc") + gain
        return s.with("hype" to (s.n("hype") + acc / 1000).coerceAtMost(1500), "acc" to acc % 1000)
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "focus" -> if (focuses.any { it.first == value }) Result.success(ActResult(s.withS("focus" to value))) else invalid("focus")
        "launch" -> {
            if (s.n("hype") < 200) invalid("Build more hype first")
            else {
                val luck = 0.6 + 0.8 * ctx.rng("launch", s.n("launches")).nextDouble()
                val quality = (s.n("hype").milli() * luck * 1000).toLong()
                Result.success(ActResult(s.with("quality" to quality, "launchedAt" to ctx.minute, "hype" to 0, "launches" to s.n("launches") + 1)))
            }
        }
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val threshold = when (policy) { ManagerPolicy.Safe -> 1000L; ManagerPolicy.Balanced -> 700L; ManagerPolicy.Aggressive -> 400L }
        val focused = s.withS("focus" to if (policy == ManagerPolicy.Aggressive) "ship" else "balanced")
        val productFading = lifecycle(def, ctx.minute - focused.n("launchedAt")) < 0.5
        return if (focused.n("hype") >= threshold && productFading) act(def, focused, "launch", "", ctx).getOrNull() ?: ActResult(focused) else ActResult(focused)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val life = lifecycle(def, ctx.minute - s.n("launchedAt"))
        return MechanicView(
            title = "R&D & launches",
            summary = "Hype builds over time. Launch when the market is hungry; products ramp up, peak, then fade.",
            stats = listOf(
                ViewStat("Current product", if (s.n("launches") == 0L) "None yet" else "Quality ${"%.0f".format(s.n("quality") / 10.0)} · ${pct(life)} of peak", if (life > 0.6) Tone.Good else Tone.Neutral),
                ViewStat("Products launched", s.n("launches").toString()),
            ),
            controls = listOf(
                ViewControl.Progress("Hype", (s.n("hype") / 1000.0).coerceAtMost(1.0), "${s.n("hype") / 10}%"),
                ViewControl.Choice("focus", "Team focus", "Research builds hype faster but sells less today.", focuses.map { ViewOption(it.first, it.second, "Sales ×${it.third.first} · hype ×${it.third.second}") }, s.s("focus")),
                ViewControl.Action("launch", "Launch a product", "Uses all current hype. Quality depends on hype and a bit of luck.", "Launch", null, s.n("hype") >= 200),
            ),
        )
    }
}
