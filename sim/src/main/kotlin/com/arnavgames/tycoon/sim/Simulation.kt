package com.arnavgames.tycoon.sim

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.time.FakeClock

/** Named moments whose real-time timing we measure against docs/ECONOMY.md targets. */
object Milestones {
    const val FIRST_LEVEL = "first_level"
    const val FIRST_MANAGER = "first_manager"
    const val RESTAURANT_OPEN = "restaurant_open"
    const val LOGISTICS_OPEN = "logistics_open"
    const val CHAPTER1_DONE = "chapter1_done"
    const val CHAPTER2_DONE = "chapter2_done"
    const val NW_10K = "networth_10k"
    const val NW_1M = "networth_1m"
    const val NW_100M = "networth_100m"
    const val NW_10B = "networth_10b"
    const val HQ_5 = "hq_level_5"

    val ALL = listOf(FIRST_LEVEL, FIRST_MANAGER, RESTAURANT_OPEN, CHAPTER1_DONE, LOGISTICS_OPEN, NW_10K, NW_1M, HQ_5, CHAPTER2_DONE, NW_100M, NW_10B)
}

/** One sample of the economy over real time (for curves and dead-zone analysis). */
data class Sample(val realMinutes: Long, val netWorthLog10: Double, val incomePerSecLog10: Double, val cashOverNextCost: Double)

data class RunResult(
    val bot: String,
    val seed: Long,
    /** Milestone → real minutes after install (absent = not reached). */
    val milestones: Map<String, Double>,
    val samples: List<Sample>,
    /** Active-session minutes in which nothing meaningful was affordable. */
    val deadMinutes: Int,
    val sessionMinutes: Int,
    /** Longest stretch (real hours) with no net-worth growth while the player kept playing. */
    val longestStallHours: Double,
    val finalNetWorth: Money,
    val error: String? = null,
)

/**
 * Plays one bot on one seed for [days] real days. Sessions advance the engine in [stepSeconds] live steps with
 * the bot acting each step; gaps between sessions are credited exactly like the app does (offline cap applies,
 * uncredited time is lost).
 */
class Simulation(private val engine: GameEngine, private val contentHash: String, private val stepSeconds: Int = 5) {

    fun run(bot: Bot, seed: Long, days: Int): RunResult = try {
        runInner(bot, seed, days)
    } catch (e: Exception) {
        RunResult(bot.name, seed, emptyMap(), emptyList(), 0, 0, 0.0, Money.ZERO, error = "${e.javaClass.simpleName}: ${e.message}")
    }

    private fun runInner(bot: Bot, seed: Long, days: Int): RunResult {
        val rng = GameRandom.stream(seed, "bot:${bot.name}")
        var s = engine.newGame(seed, contentHash, FakeClock())
        val milestones = mutableMapOf<String, Double>()
        val samples = mutableListOf<Sample>()
        var deadMinutes = 0
        var sessionMinutes = 0
        val eco = engine.economy

        // Session timetable in real minutes. Day 0 starts with a longer first session at install time.
        val jitter = rng.nextInt(-45, 46)
        val sessions = mutableListOf(0L to bot.schedule.firstSessionMinutes)
        for (extra in bot.schedule.day0ExtraSessions) sessions += (extra + jitter / 3).toLong() to bot.schedule.sessionMinutes
        for (d in 0 until days) for (start in bot.schedule.sessionStarts) {
            val t = d * 1440L + start + jitter - bot.schedule.sessionStarts.first()
            if (t > bot.schedule.firstSessionMinutes && sessions.none { kotlin.math.abs(it.first - t) < 60 }) sessions += t to bot.schedule.sessionMinutes
        }
        sessions.sortBy { it.first }
        var realMs = 0L
        var lastNw = Money.ZERO
        var lastGrowthMinute = 0L
        var longestStall = 0.0

        fun record(minute: Double) {
            val nw = eco.netWorth(s)
            if (s.stats.levelsBought > 0) milestones.putIfAbsent(Milestones.FIRST_LEVEL, minute)
            if (s.businesses.values.any { it.managerId != null }) milestones.putIfAbsent(Milestones.FIRST_MANAGER, minute)
            if ("restaurants" in s.businesses) milestones.putIfAbsent(Milestones.RESTAURANT_OPEN, minute)
            if ("logistics" in s.businesses) milestones.putIfAbsent(Milestones.LOGISTICS_OPEN, minute)
            if (s.progress.chapter >= 2) milestones.putIfAbsent(Milestones.CHAPTER1_DONE, minute)
            if (s.progress.chapter >= 3 || (s.progress.chapter == 2 && engine.content.chapters.first { it.number == 2 }.missions.all { it.id in s.progress.completedMissions })) {
                milestones.putIfAbsent(Milestones.CHAPTER2_DONE, minute)
            }
            if (nw >= Money.ofDollars(10_000)) milestones.putIfAbsent(Milestones.NW_10K, minute)
            if (nw >= Money.ofDollars(1_000_000)) milestones.putIfAbsent(Milestones.NW_1M, minute)
            if (nw >= Money.ofDollars(100_000_000)) milestones.putIfAbsent(Milestones.NW_100M, minute)
            if (nw >= Money.ofDollars(10_000_000_000)) milestones.putIfAbsent(Milestones.NW_10B, minute)
            if (s.hq.level >= 5) milestones.putIfAbsent(Milestones.HQ_5, minute)
        }

        for ((startMin, lengthMin) in sessions) {
            // Offline gap until this session.
            val gapMs = startMin * 60_000 - realMs
            if (gapMs > 0) {
                val credit = minOf(gapMs, eco.offlineCapMs(s))
                s = engine.advance(s, credit).state
                realMs += gapMs
            }
            val steps = lengthMin * 60 / stepSeconds
            var minuteHadOption = false
            for (i in 0 until steps) {
                // Taps during the early part of the session, batched per step (combo builds within the step).
                val tappingSeconds = bot.schedule.tapMinutesPerSession * 60
                if (i * stepSeconds < tappingSeconds && bot.schedule.tapsPerSecond > 0) {
                    var taps = (bot.schedule.tapsPerSecond * stepSeconds).toInt()
                    while (taps > 0) {
                        val n = minOf(taps, 50)
                        s = engine.tryApply(s, Command.HustleTap(n))
                        taps -= n
                    }
                }
                val before = s
                s = bot.act(engine, s, rng, sessionStart = i == 0)
                if (s.stats.levelsBought != before.stats.levelsBought || s.businesses.size != before.businesses.size ||
                    s.hq.level != before.hq.level || s.timers.size > before.timers.size
                ) minuteHadOption = true
                s = engine.advance(s, stepSeconds * 1000L).state
                realMs += stepSeconds * 1000L
                if ((i + 1) * stepSeconds % 60 == 0) {
                    sessionMinutes++
                    if (!minuteHadOption) deadMinutes++
                    minuteHadOption = false
                    record(realMs / 60_000.0)
                }
            }
            // Sample once per session end.
            val nw = eco.netWorth(s)
            val minuteNow = realMs / 60_000
            if (nw > lastNw * 101L / 100L) {
                longestStall = maxOf(longestStall, (minuteNow - lastGrowthMinute) / 60.0)
                lastGrowthMinute = minuteNow
                lastNw = nw
            }
            samples += Sample(minuteNow, nw.log10Dollars(), eco.totalNetRate(s).perSecond().log10Dollars(), cashOverNextCost(s))
            record(minuteNow.toDouble())
        }
        longestStall = maxOf(longestStall, (realMs / 60_000 - lastGrowthMinute) / 60.0)
        return RunResult(bot.name, seed, milestones, samples, deadMinutes, sessionMinutes, longestStall, eco.netWorth(s))
    }

    /** Inflation proxy: cash on hand ÷ cost of the cheapest level-up available. */
    private fun cashOverNextCost(s: GameState): Double {
        val costs = s.businesses.values.map { engine.economy.levelCost(engine.content.business(it.id), it.level, 1) }
        val cheapest = costs.minOrNull() ?: return 0.0
        return s.cash.ratioTo(cheapest)
    }
}
