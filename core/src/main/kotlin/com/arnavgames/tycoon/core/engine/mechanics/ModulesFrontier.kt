package com.arnavgames.tycoon.core.engine.mechanics

import com.arnavgames.tycoon.core.content.ManagerPolicy
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.MechanicState
import kotlin.math.pow

// Frontier industries: Telecom & Data, Airline, Pharma, Sports Franchise, Space.

private fun ms(kind: String) = MechanicState.Module(kind)
private fun Long.milli() = this / 1000.0
private val fares = (70..160 step 10).toList()

/**
 * TELECOM & DATA: capacity and utilization. Demand grows every minute; running hot (above 95%) risks outages, and
 * spare capacity earns nothing. Build ahead of demand. Owning Tech grows demand faster; owning Energy cuts upkeep.
 */
object TelecomModule : MechanicModule {
    override val kind = "telecom"
    override val paramKeys = setOf("growthPerMinute", "buildSeconds")

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("demand" to 700_000, "capacity" to 1_000)

    fun utilization(s: MechanicState.Module) = (s.n("demand") / 1_000_000.0) / (s.n("capacity") / 1000.0)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val u = utilization(s).coerceAtMost(1.2)
        val outage = if (s.n("outageUntil") > ctx.minute) 0.3 else 1.0
        return (0.4 + 1.2 * minOf(u, 1.0)) * outage
    }

    override fun upkeepFactor(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) = if (ctx.owns("energy")) 0.85 else 1.0

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val u = utilization(s)
        val growth = (def.p("growthPerMinute", 400.0) * if (ctx.owns("tech")) 1.25 else 1.0).toLong()
        var ns = if (u < 1.2) s.with("demand" to s.n("demand") + growth) else s
        if (u > 0.95 && ns.n("outageUntil") <= ctx.minute && ctx.rng("outage", ctx.minute).chance(0.002 * (u - 0.95) * 100)) {
            ns = ns.with("outageUntil" to ctx.minute + 60, "outages" to ns.n("outages") + 1)
        }
        return ns
    }

    private fun buildCost(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Money =
        ctx.cost(def.p("buildSeconds", 400.0) * (s.n("capacity") / 1000.0).pow(2))

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> =
        if (action == "build") Result.success(ActResult(s.with("capacity" to s.n("capacity") + 250), buildCost(def, s, ctx))) else invalid("unknown action")

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val trigger = when (policy) { ManagerPolicy.Safe -> 0.8; ManagerPolicy.Balanced -> 0.88; ManagerPolicy.Aggressive -> 0.95 }
        val cost = buildCost(def, s, ctx)
        return if (utilization(s) > trigger && cost <= budget) ActResult(s.with("capacity" to s.n("capacity") + 250), cost) else ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val u = utilization(s)
        return MechanicView(
            title = "Capacity & utilization",
            summary = "Demand keeps growing. Spare capacity earns nothing; running above 95% risks outages. Build just ahead of demand.",
            stats = listOf(
                ViewStat("Network status", if (s.n("outageUntil") > ctx.minute) "Outage · ${s.n("outageUntil") - ctx.minute} min" else "Online", if (s.n("outageUntil") > ctx.minute) Tone.Bad else Tone.Good),
                ViewStat("Capacity", "%.2f units".format(s.n("capacity") / 1000.0)),
                ViewStat("Outages", s.n("outages").toString()),
            ),
            controls = listOf(
                ViewControl.Progress("Utilization", u.coerceAtMost(1.0), "${pct(u)}" + if (u > 0.95) " · running hot" else if (u < 0.6) " · lots of spare capacity" else ""),
                ViewControl.Action("build", "Build capacity", "+0.25 units. Each build costs more than the last.", "Build", buildCost(def, s, ctx)),
            ),
        )
    }
}

/**
 * AIRLINE: fleet and network. Fares trade load factor for revenue. Planes need maintenance checks (a short
 * grounding); skipping checks past 48 hours raises the risk of a costly incident.
 */
object AirlineModule : MechanicModule {
    override val kind = "airline"
    override val paramKeys = setOf("elasticity", "groundMinutes")

    fun loadFactor(def: MechanicDef.Module, fare: Int, ctx: MechCtx) = (0.85 * tourism(ctx) * (100.0 / fare).pow(def.p("elasticity", 1.5))).coerceAtMost(1.0)
    private fun value(def: MechanicDef.Module, fare: Int, ctx: MechCtx) = loadFactor(def, fare, ctx) * fare / 100.0

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("fare" to 100, "lastCheck" to ctx.minute)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val grounded = if (s.n("groundedUntil") > ctx.minute) 0.5 else 1.0
        val incident = if (s.n("incidentUntil") > ctx.minute) 0.25 else 1.0
        return value(def, s.n("fare").toInt(), ctx) / 0.85 * grounded * incident
    }

    fun hoursSinceCheck(s: MechanicState.Module, ctx: MechCtx) = (ctx.minute - s.n("lastCheck")) / 60.0

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val h = hoursSinceCheck(s, ctx)
        if (h <= 48 || s.n("incidentUntil") > ctx.minute) return s
        return if (ctx.rng("incident", ctx.minute).chance(0.0003 * (h - 48))) s.with("incidentUntil" to ctx.minute + 180, "lastCheck" to ctx.minute, "incidents" to s.n("incidents") + 1) else s
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "fare" -> value.toLongOrNull()?.takeIf { it.toInt() in fares }?.let { Result.success(ActResult(s.with("fare" to it))) } ?: invalid("fare")
        "maintain" -> if (s.n("groundedUntil") > ctx.minute) invalid("Checks already under way")
        else Result.success(ActResult(s.with("groundedUntil" to ctx.minute + def.p("groundMinutes", 30.0).toLong(), "lastCheck" to ctx.minute)))
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val best = fares.maxBy { value(def, it, ctx) }
        var ns = s.with("fare" to ((100 + (best - 100) * skill).toInt() / 10 * 10).toLong().coerceIn(70, 160))
        val due = when (policy) { ManagerPolicy.Safe -> 24.0; ManagerPolicy.Balanced -> 36.0; ManagerPolicy.Aggressive -> 56.0 }
        if (hoursSinceCheck(ns, ctx) >= due && ns.n("groundedUntil") <= ctx.minute) ns = act(def, ns, "maintain", "", ctx).getOrNull()?.state ?: ns
        return ActResult(ns)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val h = hoursSinceCheck(s, ctx)
        return MechanicView(
            title = "Fleet & network",
            summary = "Set fares for the season and keep up with maintenance. Skipping checks past 48 hours risks an incident.",
            stats = listOf(
                ViewStat("Load factor", pct(loadFactor(def, s.n("fare").toInt(), ctx))),
                ViewStat("Last maintenance", "%.0f h ago".format(h), if (h > 48) Tone.Bad else if (h > 36) Tone.Warn else Tone.Good),
                ViewStat("Status", when { s.n("incidentUntil") > ctx.minute -> "Incident investigation"; s.n("groundedUntil") > ctx.minute -> "Checks in progress"; else -> "Flying" }, if (s.n("incidentUntil") > ctx.minute) Tone.Bad else Tone.Good),
            ),
            controls = listOf(
                ViewControl.Choice("fare", "Fares", "Percent of the standard fare.", fares.map { ViewOption(it.toString(), "$it%", "Load ${pct(loadFactor(def, it, ctx))}") }, s.n("fare").toString()),
                ViewControl.Action("maintain", "Maintenance checks", "Grounds half the fleet for ${def.p("groundMinutes", 30.0).toInt()} minutes and resets the clock.", "Start checks", null, s.n("groundedUntil") <= ctx.minute),
            ),
        )
    }
}

/**
 * PHARMA: the trial pipeline. Fund a candidate through Phase I → II → III; each phase takes time and may fail.
 * Approved drugs earn under patent for a while, then generics arrive. Owning Tech improves trial odds.
 */
object PharmaModule : MechanicModule {
    override val kind = "pharma"
    override val paramKeys = setOf("patentMinutes")
    data class Phase(val name: String, val minutes: Long, val odds: Double, val costSeconds: Double)
    val phases = listOf(Phase("Phase I", 60, 0.70, 200.0), Phase("Phase II", 180, 0.45, 500.0), Phase("Phase III", 360, 0.60, 1200.0))

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("phase" to 0, "candidate" to 1)

    private fun drugValue(def: MechanicDef.Module, approvedAt: Long, ctx: MechCtx): Double {
        if (approvedAt <= 0) return 0.0
        val age = ctx.minute - approvedAt
        return if (age < def.p("patentMinutes", 2160.0)) 0.6 else 0.15
    }

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) =
        0.4 + (0 until 3).sumOf { drugValue(def, s.n("drug$it"), ctx) }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val phase = s.n("phase").toInt()
        // 0 = idle, negative = waiting to fund the next phase; only a positive phase is a running trial.
        if (phase <= 0 || s.n("trialUntil") > ctx.minute) return s
        val p = phases[phase - 1]
        val odds = (p.odds + if (ctx.owns("tech")) 0.10 else 0.0).coerceAtMost(0.95)
        val success = ctx.rng("trial:$phase", s.n("candidate")).chance(odds)
        return when {
            !success -> s.with("phase" to 0, "candidate" to s.n("candidate") + 1, "failures" to s.n("failures") + 1)
            phase < 3 -> s.with("phase" to -(phase + 1).toLong()) // negative = ready to fund the next phase
            else -> {
                // Approved: replace the oldest drug slot.
                val slot = (0 until 3).minBy { s.n("drug$it") }
                s.with("drug$slot" to ctx.minute, "phase" to 0, "candidate" to s.n("candidate") + 1, "approvals" to s.n("approvals") + 1)
            }
        }
    }

    /** The phase the candidate is ready to start (1-based), or null while a trial is running. */
    fun nextPhase(s: MechanicState.Module): Int? = when {
        s.n("phase") == 0L -> 1
        s.n("phase") < 0 -> (-s.n("phase")).toInt()
        else -> null
    }

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> {
        if (action != "fund") return invalid("unknown action")
        val next = nextPhase(s) ?: return invalid("A trial is already running")
        val p = phases[next - 1]
        return Result.success(ActResult(s.with("phase" to next.toLong(), "trialUntil" to ctx.minute + p.minutes), ctx.cost(p.costSeconds)))
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val next = nextPhase(s) ?: return ActResult(s)
        val cost = ctx.cost(phases[next - 1].costSeconds)
        return if (cost <= budget) act(def, s, "fund", "", ctx).getOrNull() ?: ActResult(s) else ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val next = nextPhase(s)
        val running = s.n("phase") > 0
        val controls = mutableListOf<ViewControl>()
        if (running) {
            val p = phases[s.n("phase").toInt() - 1]
            val left = s.n("trialUntil") - ctx.minute
            controls += ViewControl.Progress("${p.name} trial", 1.0 - left.toDouble() / p.minutes, "$left min left · success odds ${pct(p.odds + if (ctx.owns("tech")) 0.1 else 0.0)}")
        } else if (next != null) {
            val p = phases[next - 1]
            controls += ViewControl.Action("fund", "Fund ${p.name}", "${p.minutes / 60.0} h · success odds ${pct(p.odds + if (ctx.owns("tech")) 0.1 else 0.0)}", "Fund trial", ctx.cost(p.costSeconds))
        }
        val active = (0 until 3).count { drugValue(def, s.n("drug$it"), ctx) >= 0.6 }
        return MechanicView(
            title = "Trial pipeline",
            summary = "Fund candidates through three trial phases. Approved drugs earn strongly under patent, then generics arrive.",
            stats = listOf(
                ViewStat("Candidate", "#${s.n("candidate")}" + if (running) " · in ${phases[s.n("phase").toInt() - 1].name}" else ""),
                ViewStat("Drugs under patent", active.toString(), if (active > 0) Tone.Good else Tone.Neutral),
                ViewStat("Approvals / failures", "${s.n("approvals")} / ${s.n("failures")}"),
            ),
            controls = controls,
        )
    }
}

/**
 * SPORTS FRANCHISE: season and roster. A matchday every four hours; your roster strength is the win chance. Wins
 * build the brand; a strong ten-match season wins a championship (a permanent bonus). Owning Media sells TV rights.
 */
object SportsModule : MechanicModule {
    override val kind = "sports"
    override val paramKeys = setOf("matchMinutes", "boostSeconds")

    private fun matchday(def: MechanicDef.Module, ctx: MechCtx) = ctx.minute / def.p("matchMinutes", 240.0).toLong()

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("strength" to 550, "brand" to 300, "lastMatch" to matchday(def, ctx))

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val rights = if (ctx.owns("media")) 0.1 else 0.0
        return (0.55 + s.n("brand").milli() * 1.1 + rights) * (1 + 0.04 * s.n("trophies").coerceAtMost(10))
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        var ns = if (ctx.minute % 10 == 0L) s.with("strength" to (s.n("strength") - 1).coerceAtLeast(300)) else s
        val md = matchday(def, ctx)
        if (md == ns.n("lastMatch")) return ns
        val win = ctx.rng("match", md).chance(ns.n("strength").milli())
        val brand = (ns.n("brand") + if (win) 60 else -40).coerceIn(0, 1000)
        var played = ns.n("played") + 1
        var wins = ns.n("wins") + if (win) 1 else 0
        var trophies = ns.n("trophies")
        if (played >= 10) {
            if (wins >= 7) trophies++
            played = 0; wins = 0
        }
        ns = ns.with("lastMatch" to md, "brand" to brand, "played" to played, "wins" to wins, "trophies" to trophies, "lastResult" to if (win) 1 else 0)
        return ns
    }

    private fun boostCost(def: MechanicDef.Module, ctx: MechCtx) = ctx.cost(def.p("boostSeconds", 300.0))

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> =
        if (action == "roster") {
            if (s.n("strength") >= 950) invalid("The roster is already elite")
            else Result.success(ActResult(s.with("strength" to (s.n("strength") + 100).coerceAtMost(950)), boostCost(def, ctx)))
        } else invalid("unknown action")

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        val floor = when (policy) { ManagerPolicy.Safe -> 550L; ManagerPolicy.Balanced -> 650L; ManagerPolicy.Aggressive -> 800L }
        val cost = boostCost(def, ctx)
        return if (s.n("strength") < floor && cost <= budget) ActResult(s.with("strength" to (s.n("strength") + 100).coerceAtMost(950)), cost) else ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val len = def.p("matchMinutes", 240.0).toLong()
        return MechanicView(
            title = "Season & roster",
            summary = "Roster strength is your win chance each matchday. Wins build the brand; seven wins in a ten-match season take the title.",
            stats = listOf(
                ViewStat("Season record", "${s.n("wins")} – ${s.n("played") - s.n("wins")} (of 10)"),
                ViewStat("Last match", if (s.n("played") == 0L && s.n("trophies") == 0L) "—" else if (s.n("lastResult") == 1L) "Won" else "Lost", if (s.n("lastResult") == 1L) Tone.Good else Tone.Neutral),
                ViewStat("Next matchday", "${len - ctx.minute % len} min"),
                ViewStat("Championships", s.n("trophies").toString(), if (s.n("trophies") > 0) Tone.Good else Tone.Neutral),
            ),
            controls = listOf(
                ViewControl.Progress("Roster strength", s.n("strength").milli(), "Win chance ${pct(s.n("strength").milli())} · fades slowly without investment"),
                ViewControl.Progress("Brand", s.n("brand").milli(), "${s.n("brand") / 10}% · drives merchandise and tickets"),
                ViewControl.Action("roster", "Sign players", "+10% roster strength.", "Sign", boostCost(def, ctx), s.n("strength") < 950),
            ),
        )
    }
}

/**
 * SPACE: missions with long timers. Success odds grow with reliability; every failure teaches you something (+1
 * reliability). Satellites add permanent income (more with Telecom); tourism and asteroid missions pay big bursts.
 */
object SpaceModule : MechanicModule {
    override val kind = "space"
    override val paramKeys = setOf("reliabilitySeconds")
    data class Mission(val id: String, val name: String, val minutes: Long, val costSeconds: Double, val result: String)
    val missions = listOf(
        Mission("satellite", "Comms satellite", 240, 600.0, "+ permanent income"),
        Mission("tourism", "Orbital tourism", 480, 1500.0, "×2 income for 6 h"),
        Mission("asteroid", "Asteroid mining", 1440, 4000.0, "×3 income for 12 h"),
    )

    override fun initial(def: MechanicDef.Module, ctx: MechCtx) = ms(kind).with("reliability" to 0, "satellites" to 0)

    fun odds(s: MechanicState.Module, ctx: MechCtx) = (0.55 + 0.04 * s.n("reliability") + if (ctx.owns("tech")) 0.05 else 0.0).coerceAtMost(0.95)

    override fun efficiency(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): Double {
        val satEach = if (ctx.owns("telecom")) 0.225 else 0.15
        val base = 0.4 + satEach * s.n("satellites").coerceAtMost(8)
        val boost = if (s.n("boostUntil") > ctx.minute) s.n("boostMilli").milli() else 1.0
        return base * boost
    }

    override fun minute(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicState.Module {
        val type = s.s("mission")
        if (type.isEmpty() || s.n("missionUntil") > ctx.minute) return s
        val success = ctx.rng("mission", s.n("launches")).chance(odds(s, ctx))
        val cleared = s.withS("mission" to "").with("launches" to s.n("launches") + 1, "lastSuccess" to if (success) 1 else 0)
        if (!success) return cleared.with("reliability" to (s.n("reliability") + 1).coerceAtMost(10))
        return when (type) {
            "satellite" -> cleared.with("satellites" to s.n("satellites") + 1)
            "tourism" -> cleared.with("boostUntil" to ctx.minute + 360, "boostMilli" to 2000)
            else -> cleared.with("boostUntil" to ctx.minute + 720, "boostMilli" to 3000)
        }
    }

    private fun relCost(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx) = ctx.cost(def.p("reliabilitySeconds", 800.0) * (s.n("reliability") + 1))

    override fun act(def: MechanicDef.Module, s: MechanicState.Module, action: String, value: String, ctx: MechCtx): Result<ActResult> = when (action) {
        "launch" -> {
            val m = missions.firstOrNull { it.id == value } ?: return invalid("mission")
            if (s.s("mission").isNotEmpty()) invalid("A mission is already in flight")
            else Result.success(ActResult(s.withS("mission" to m.id).with("missionUntil" to ctx.minute + m.minutes), ctx.cost(m.costSeconds)))
        }
        "reliability" -> if (s.n("reliability") >= 10) invalid("Reliability is maxed") else Result.success(ActResult(s.with("reliability" to s.n("reliability") + 1), relCost(def, s, ctx)))
        else -> invalid("unknown action")
    }

    override fun manage(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx, skill: Double, policy: ManagerPolicy, budget: Money): ActResult {
        if (s.s("mission").isNotEmpty()) return ActResult(s)
        val m = if (policy == ManagerPolicy.Aggressive && s.n("satellites") >= 3) missions[1] else missions[0]
        return if (ctx.cost(m.costSeconds) <= budget) act(def, s, "launch", m.id, ctx).getOrNull() ?: ActResult(s) else ActResult(s)
    }

    override fun view(def: MechanicDef.Module, s: MechanicState.Module, ctx: MechCtx): MechanicView {
        val inFlight = s.s("mission")
        val controls = mutableListOf<ViewControl>()
        if (inFlight.isNotEmpty()) {
            val m = missions.first { it.id == inFlight }
            val left = s.n("missionUntil") - ctx.minute
            controls += ViewControl.Progress("${m.name} in flight", 1.0 - left.toDouble() / m.minutes, "$left min to result · odds ${pct(odds(s, ctx))}")
        } else {
            missions.forEach { m -> controls += ViewControl.Action("launch", m.name, "${m.minutes / 60} h · ${m.result} · odds ${pct(odds(s, ctx))}", "Launch", ctx.cost(m.costSeconds), value = m.id) }
        }
        controls += ViewControl.Action("reliability", "Reliability programme (level ${s.n("reliability")})", "+4% success odds on every mission.", "Invest", if (s.n("reliability") >= 10) null else relCost(def, s, ctx), s.n("reliability") < 10)
        return MechanicView(
            title = "Missions",
            summary = "Launch when the odds are good enough. Failures raise reliability; satellites keep paying for good.",
            stats = listOf(
                ViewStat("Satellites in orbit", s.n("satellites").toString(), if (s.n("satellites") > 0) Tone.Good else Tone.Neutral),
                ViewStat("Mission success odds", pct(odds(s, ctx))),
                ViewStat("Boost", if (s.n("boostUntil") > ctx.minute) "×${s.n("boostMilli") / 1000} for ${s.n("boostUntil") - ctx.minute} min" else "None", if (s.n("boostUntil") > ctx.minute) Tone.Good else Tone.Neutral),
                ViewStat("Last mission", if (s.n("launches") == 0L) "—" else if (s.n("lastSuccess") == 1L) "Success" else "Failed (reliability +1)"),
            ),
            controls = controls,
        )
    }
}

/** All module mechanics, by kind. */
object MechanicRegistry {
    val modules: Map<String, MechanicModule> = listOf(
        FarmlandModule, FactoryModule, FashionModule, MiningModule, EnergyModule,
        HotelsModule, BankModule, ConstructionModule, MediaModule, TechModule,
        TelecomModule, AirlineModule, PharmaModule, SportsModule, SpaceModule,
    ).associateBy { it.kind }

    fun of(kind: String): MechanicModule = modules[kind] ?: error("unknown mechanic module '$kind'")

    /**
     * Cross-business income synergies that don't belong to one module: Farmland supplies Restaurants (+15%),
     * Media campaigns boost a chosen business (+15%).
     */
    fun crossIncome(owned: Set<String>, businessId: String, mediaState: MechanicState.Module?, minute: Long): Double {
        var m = 1.0
        if (businessId == "restaurants" && "farmland" in owned) m *= 1.15
        if (mediaState != null) m *= MediaModule.campaignBoost(mediaState, businessId, minute)
        return m
    }

    /** Cross-business upkeep synergies: Energy powers Factory and Hotels at cost (Telecom handles its own). */
    fun crossUpkeep(owned: Set<String>, businessId: String): Double =
        if ("energy" in owned && businessId in setOf("factory", "hotels")) 0.85 else 1.0
}
