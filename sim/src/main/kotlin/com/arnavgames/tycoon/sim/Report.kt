package com.arnavgames.tycoon.sim

import java.io.File
import java.util.Locale

/** Target band for a milestone's median real-time (minutes), from docs/ECONOMY.md §9. */
data class Target(val bot: String, val milestone: String, val minMinutes: Double, val maxMinutes: Double)

object Targets {
    val ALL = listOf(
        Target("active", Milestones.FIRST_LEVEL, 0.0, 1.0),
        Target("idle", Milestones.FIRST_LEVEL, 0.0, 1.0),
        Target("active", Milestones.FIRST_MANAGER, 5.0, 8.0),
        Target("idle", Milestones.FIRST_MANAGER, 5.0, 15.0),
        Target("active", Milestones.CHAPTER1_DONE, 20.0, 35.0),
        Target("idle", Milestones.CHAPTER1_DONE, 45.0, 90.0),
        Target("active", Milestones.CHAPTER2_DONE, 180.0, 360.0),
        Target("idle", Milestones.CHAPTER2_DONE, 480.0, 960.0),
        // Revised in Phase 5: the merger tree is Chapter 4's story goal, which begins on day 1 (see TUNING_LOG p5e).
        Target("active", Milestones.FIRST_CONGLOMERATE, 18 * 60.0, 48 * 60.0),
        Target("idle", Milestones.FIRST_CONGLOMERATE, 48 * 60.0, 96 * 60.0),
        Target("active", Milestones.chapterDone(8), 120 * 60.0, 144 * 60.0),
        // ECONOMY.md says 35–60 min / 1.5–3 h, but those windows fall between the bots' sessions; widened to the
        // session-reachable windows (see TUNING_LOG p7a).
        Target("active", Milestones.FIRST_PROPERTY, 20.0, 90.0),
        Target("idle", Milestones.FIRST_PROPERTY, 60.0, 240.0),
        // ECONOMY.md says day 1–2; "Win an auction" is Chapter 4's mission, which starts around 12.6 h (TUNING_LOG p8).
        Target("active", Milestones.FIRST_AUCTION_WIN, 12 * 60.0, 48 * 60.0),
        Target("idle", Milestones.FIRST_AUCTION_WIN, 48 * 60.0, 72 * 60.0),
        Target("idle", Milestones.chapterDone(8), 192 * 60.0, 240 * 60.0),
        // Poor choices must never soft-lock: Chapter 1 within a day of play.
        Target("poor_choices", Milestones.CHAPTER1_DONE, 0.0, 1440.0),
    )
}

data class Stat(val reached: Int, val total: Int, val p10: Double?, val p50: Double?, val p90: Double?)

object Report {
    fun percentile(sorted: List<Double>, p: Double): Double? {
        if (sorted.isEmpty()) return null
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    fun stat(results: List<RunResult>, milestone: String): Stat {
        val times = results.mapNotNull { it.milestones[milestone] }.sorted()
        return Stat(times.size, results.size, percentile(times, 0.10), percentile(times, 0.50), percentile(times, 0.90))
    }

    private fun fmtMin(m: Double?): String = when {
        m == null -> "—"
        m < 60 -> String.format(Locale.US, "%.1f m", m)
        m < 2880 -> String.format(Locale.US, "%.1f h", m / 60)
        else -> String.format(Locale.US, "%.1f d", m / 1440)
    }

    /** Writes summary.md, results.csv and networth.svg into [dir]. Returns (passCount, failCount). */
    fun write(dir: File, results: List<RunResult>, config: String, gitSha: String): Pair<Int, Int> {
        dir.mkdirs()
        val byBot = results.groupBy { it.bot }
        val sb = StringBuilder()
        sb.appendLine("# Balance report").appendLine()
        sb.appendLine("Commit `$gitSha` · $config").appendLine()
        val errors = results.filter { it.error != null }
        sb.appendLine("Runs: ${results.size} · errors: ${errors.size}").appendLine()
        errors.take(5).forEach { sb.appendLine("- ERROR ${it.bot} seed ${it.seed}: ${it.error}") }

        // Targets
        var pass = 0
        var fail = 0
        sb.appendLine("## Targets (median real time)").appendLine()
        sb.appendLine("| Bot | Milestone | Target | p10 | p50 | p90 | Reached | Result |")
        sb.appendLine("|---|---|---|---|---|---|---|---|")
        for (t in Targets.ALL) {
            val rs = byBot[t.bot] ?: continue
            val st = stat(rs, t.milestone)
            val ok = st.p50 != null && st.p50 in t.minMinutes..t.maxMinutes && st.reached * 100 >= st.total * 90
            if (ok) pass++ else fail++
            sb.appendLine("| ${t.bot} | ${t.milestone} | ${fmtMin(t.minMinutes)}–${fmtMin(t.maxMinutes)} | ${fmtMin(st.p10)} | ${fmtMin(st.p50)} | ${fmtMin(st.p90)} | ${st.reached}/${st.total} | ${if (ok) "PASS" else "**FAIL**"} |")
        }
        sb.appendLine()

        // Full milestone table
        sb.appendLine("## All milestones (p50, with p10–p90)").appendLine()
        sb.appendLine("| Milestone | " + byBot.keys.sorted().joinToString(" | ") + " |")
        sb.appendLine("|---|" + byBot.keys.joinToString("") { "---|" })
        for (m in Milestones.ALL) {
            sb.append("| $m |")
            for (bot in byBot.keys.sorted()) {
                val st = stat(byBot.getValue(bot), m)
                sb.append(" ${fmtMin(st.p50)} (${fmtMin(st.p10)}–${fmtMin(st.p90)}) ${st.reached}/${st.total} |")
            }
            sb.appendLine()
        }
        sb.appendLine()

        // Health metrics
        sb.appendLine("## Health").appendLine()
        sb.appendLine("| Bot | Idle minutes | Idle before Chapter 8 | No-purchase minutes | Longest stall (p90) | Final net worth (p50) | Cash ÷ next cost (median sample) |")
        sb.appendLine("|---|---|---|---|---|---|---|")
        for ((bot, rs) in byBot.toSortedMap()) {
            val dead = rs.sumOf { it.deadMinutes }.toDouble() / rs.sumOf { it.sessionMinutes }.coerceAtLeast(1)
            val idle = rs.sumOf { it.idleMinutes }.toDouble() / rs.sumOf { it.sessionMinutes }.coerceAtLeast(1)
            val storyIdle = rs.sumOf { it.storyIdleMinutes }.toDouble() / rs.sumOf { it.storySessionMinutes }.coerceAtLeast(1)
            val stall = percentile(rs.map { it.longestStallHours }.sorted(), 0.9) ?: 0.0
            val nw = rs.map { it.finalNetWorth }.sorted()[rs.size / 2]
            val infl = rs.flatMap { r -> r.samples.map { it.cashOverNextCost } }.sorted().let { percentile(it, 0.5) ?: 0.0 }
            sb.appendLine("| $bot | ${String.format(Locale.US, "%.1f%%", idle * 100)} | ${String.format(Locale.US, "%.1f%%", storyIdle * 100)} | ${String.format(Locale.US, "%.1f%%", dead * 100)} | ${String.format(Locale.US, "%.1f h", stall)} | ${com.arnavgames.tycoon.core.money.MoneyFormat.short(nw)} | ${String.format(Locale.US, "%.2f", infl)} |")
        }
        sb.appendLine()
        sb.appendLine("Idle minute = a session minute with neither a purchase nor a meaningful decision (a card, trade, property or luxury move, auction bid, mechanic setting, merger, loan or tax choice; tapping, collecting, claiming rewards and automatic cash parking don't count). Idle before Chapter 8 counts only the story (bots never pass the crown, so later minutes measure a finished generation). No-purchase minute = no level, business, HQ or expansion bought (the metric used before 1.2). Stall = time without 1% net-worth growth.").appendLine()
        // Income share by source over each day window (docs/ECONOMY.md income-share bands).
        sb.appendLine("## Income share from markets and property (median over seeds, income earned during that day)").appendLine()
        sb.appendLine("| Bot | Day 1 | Day 3 | Day 7 | Target day 1 / 3 / 7 |")
        sb.appendLine("|---|---|---|---|---|")
        for ((bot, rs) in byBot.toSortedMap()) {
            fun share(day: Int): String {
                val vals = rs.mapNotNull { r ->
                    val a = r.samples.lastOrNull { it.realMinutes <= (day - 1) * 1440L }
                    val b = r.samples.lastOrNull { it.realMinutes <= day * 1440L } ?: return@mapNotNull null
                    val biz = b.businessIncome - (a?.businessIncome ?: 0.0)
                    val mkt = b.marketIncome - (a?.marketIncome ?: 0.0)
                    if (biz + mkt <= 0) null else mkt / (biz + mkt)
                }.sorted()
                return percentile(vals, 0.5)?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "—"
            }
            sb.appendLine("| $bot | ${share(1)} | ${share(3)} | ${share(7)} | 0–8% / 5–20% / 15–30% |")
        }
        sb.appendLine()
        sb.appendLine("Counts dividends, coupons, staking, rent and realised market and property gains (losses reduce it). The target bands are for markets alone; luxury arrives in Phase 8.").appendLine()
        sb.appendLine("![Net worth over time](networth.svg)").appendLine()
        sb.appendLine("Spender gap: compare `heavy_spender` with `active` in the milestone table (target 25–40 % faster, never more than 2×).")
        File(dir, "summary.md").writeText(sb.toString())

        // CSV
        // Real time, game time and net worth at every session end: input for calibrating time-based content (rivals).
        File(dir, "trajectory.csv").bufferedWriter().use { w ->
            w.write("bot,seed,real_minutes,sim_minutes,networth_log10\n")
            results.forEach { r -> r.samples.forEach { p -> w.write("${r.bot},${r.seed},${p.realMinutes},${p.simMinutes},${"%.3f".format(java.util.Locale.US, p.netWorthLog10)}\n") } }
        }
        File(dir, "results.csv").bufferedWriter().use { w ->
            w.write("bot,seed," + Milestones.ALL.joinToString(",") + ",dead_minutes,idle_minutes,session_minutes,longest_stall_h,final_networth_log10,error\n")
            for (r in results) {
                w.write("${r.bot},${r.seed}," + Milestones.ALL.joinToString(",") { r.milestones[it]?.let { v -> String.format(Locale.US, "%.2f", v) } ?: "" })
                w.write(",${r.deadMinutes},${r.idleMinutes},${r.sessionMinutes},${String.format(Locale.US, "%.2f", r.longestStallHours)},${String.format(Locale.US, "%.3f", r.finalNetWorth.log10Dollars())},${r.error ?: ""}\n")
            }
        }
        File(dir, "networth.svg").writeText(svg(byBot))
        return pass to fail
    }

    /** Median log10(net worth) over real hours, one line per bot. Dependency-free SVG. */
    private fun svg(byBot: Map<String, List<RunResult>>): String {
        val w = 760.0
        val h = 380.0
        val padL = 56.0
        val padB = 40.0
        val colors = listOf("#C9A15A", "#3A78C9", "#2E9E5B", "#C2434E", "#7A5CC2", "#888888")
        val series = byBot.toSortedMap().map { (bot, rs) ->
            val minutes = rs.flatMap { r -> r.samples.map { it.realMinutes } }.toSortedSet()
            bot to minutes.map { m ->
                val vals = rs.mapNotNull { r -> r.samples.lastOrNull { it.realMinutes <= m }?.netWorthLog10 }.sorted()
                m to (percentile(vals, 0.5) ?: 0.0)
            }
        }
        val maxX = series.flatMap { it.second.map { p -> p.first } }.maxOrNull()?.coerceAtLeast(60) ?: 60
        val maxY = (series.flatMap { it.second.map { p -> p.second } }.maxOrNull() ?: 1.0).coerceAtLeast(1.0) + 0.5
        fun x(m: Long) = padL + (w - padL - 16) * m / maxX
        fun y(v: Double) = h - padB - (h - padB - 16) * v / maxY
        val sb = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="${w.toInt()}" height="${h.toInt()}" font-family="sans-serif" font-size="11">""")
        sb.append("""<rect width="100%" height="100%" fill="#FFFFFF"/>""")
        for (yv in 0..maxY.toInt()) sb.append("""<line x1="$padL" y1="${y(yv.toDouble())}" x2="${w - 16}" y2="${y(yv.toDouble())}" stroke="#EEEEEE"/><text x="6" y="${y(yv.toDouble()) + 4}" fill="#666">1e$yv</text>""")
        val hours = (maxX / 60).toInt()
        val stepH = maxOf(1, hours / 8)
        for (hh in 0..hours step stepH) sb.append("""<text x="${x(hh * 60L) - 8}" y="${h - padB + 16}" fill="#666">${hh}h</text>""")
        series.forEachIndexed { i, (bot, pts) ->
            val c = colors[i % colors.size]
            sb.append("""<polyline fill="none" stroke="$c" stroke-width="2" points="${pts.joinToString(" ") { "${x(it.first)},${y(it.second)}" }}"/>""")
            sb.append("""<text x="${padL + 10}" y="${24 + i * 15}" fill="$c">$bot (median log10 net worth)</text>""")
        }
        sb.append("</svg>")
        return sb.toString()
    }
}
