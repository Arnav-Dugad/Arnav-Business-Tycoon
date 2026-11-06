package com.arnavgames.tycoon.core.progression

import com.arnavgames.tycoon.core.content.AchievementDef
import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.MissionTemplateDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.Economy
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MetaState
import com.arnavgames.tycoon.core.state.MissionState

/**
 * Daily and weekly missions, achievements, the daily reward ladder, weekly event goals and the season track
 * (docs/GDD.md §12). Progress is read from lifetime counters, which are updated from game events at the end of every
 * command and every advance, so offline catch-up equals live play.
 */
class ProgressionEngine(private val content: ContentPack, private val economy: Economy) {
    private val p get() = content.progression
    private val d get() = economy.dynasty

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    private fun meta(s: GameState, f: (MetaState) -> MetaState) = s.copy(meta = f(s.meta))

    // ----- Counters -----

    /** Adds the metrics in [events] (and taps from a Hustle command) to the lifetime counters. */
    fun count(s: GameState, events: List<GameEvent>, cmd: Command? = null): GameState {
        val add = HashMap<String, Long>()
        fun inc(k: String, n: Long = 1) { add[k] = (add[k] ?: 0) + n }
        if (cmd is Command.HustleTap) inc("taps", cmd.taps.toLong())
        for (e in events) when (e) {
            is GameEvent.LevelsBought -> inc("levels", e.count.toLong())
            is GameEvent.FrenzyStarted -> inc("frenzies")
            is GameEvent.ManagerHired -> inc("managers")
            is GameEvent.ExpansionCompleted -> inc("expansions")
            is GameEvent.TradeExecuted -> inc("trades")
            is GameEvent.PropertyBought -> inc("properties")
            is GameEvent.LuxuryBought -> inc("luxury")
            is GameEvent.RenovationCompleted -> inc("renovations")
            is GameEvent.BusinessOpened -> inc("businesses")
            is GameEvent.AuctionWon -> { inc("auctions"); inc("luxury") }
            is GameEvent.TaxFiled -> if (!e.auto) inc("returns")
            is GameEvent.MarketPayout -> inc("payouts")
            is GameEvent.LoanRepaid -> inc("loans_repaid")
            is GameEvent.WorldEventStarted -> inc("events")
            is GameEvent.CardResolved -> inc("cards")
            is GameEvent.TowerCompleted -> inc("towers")
            is GameEvent.PhoenixReborn -> inc("phoenix")
            is GameEvent.BusinessSold -> inc("sold")
            else -> Unit
        }
        if (add.isEmpty()) return s
        val c = s.meta.counters.toMutableMap()
        for ((k, v) in add) c[k] = (c[k] ?: 0) + v
        return meta(s) { it.copy(counters = c) }
    }

    fun counter(s: GameState, key: String) = s.meta.counters[key] ?: 0L

    // ----- Missions -----

    private fun band(chapter: Int) = when { chapter <= 2 -> 0; chapter <= 4 -> 1; else -> 2 }

    /**
     * Cash rewards are "hours of income", but early chapters last minutes, not hours (ECONOMY §2), so rewards are
     * scaled down early to stay a bonus rather than a jackpot.
     */
    fun rewardScale(chapter: Int) = when (chapter) { 1 -> 0.02; 2 -> 0.04; 3 -> 0.1; 4 -> 0.25; 5 -> 0.5; else -> 1.0 }

    private fun assign(s: GameState, t: MissionTemplateDef, scale: Double = 1.0): MissionState {
        val hourly = economy.totalNetRate(s).over(3600).max(Money.ofDollars(1))
        val raw = t.targets.getOrElse(band(s.progress.chapter)) { t.targets.last() }
        val target = maxOf(1L, (raw * scale).toLong())
        val reward = hourly * Multiplier.ofDouble(t.rewardHours * scale.coerceAtMost(1.0) * rewardScale(s.progress.chapter))
        return if (t.metric == "earn_hours") {
            MissionState(t.id, t.metric, target, targetMoney = hourly * target, baselineMoney = s.stats.lifetimeEarned, rewardCash = reward, bullion = t.bullion, seasonXp = t.seasonXp)
        } else MissionState(t.id, t.metric, target, baseline = counter(s, t.metric), rewardCash = reward, bullion = t.bullion, seasonXp = t.seasonXp)
    }

    private fun pick(s: GameState, pool: List<MissionTemplateDef>, n: Int, stream: String, key: Long): List<MissionTemplateDef> {
        val eligible = pool.filter { it.minChapter <= s.progress.chapter }.toMutableList()
        val r = GameRandom.stream(s.worldSeed, stream, key)
        val out = mutableListOf<MissionTemplateDef>()
        while (out.size < n && eligible.isNotEmpty()) {
            val t = eligible.removeAt(r.nextInt(eligible.size))
            if (out.none { it.metric == t.metric }) out += t
        }
        return out
    }

    fun progress(s: GameState, m: MissionState): Double = when {
        m.metric == "earn_hours" -> if (!m.targetMoney.isPositive) 1.0 else (s.stats.lifetimeEarned - m.baselineMoney).ratioTo(m.targetMoney).coerceIn(0.0, 1.0)
        else -> ((counter(s, m.metric) - m.baseline).toDouble() / m.target).coerceIn(0.0, 1.0)
    }

    fun done(s: GameState, m: MissionState) = progress(s, m) >= 1.0

    /**
     * Housekeeping at the end of every command and advance: new dailies on a new game day, new weeklies and event goals
     * in a new week, a new season, and achievement unlocks. Pure function of the state, so split advances agree.
     */
    fun refresh(s0: GameState, checkAchievements: Boolean = true): GameState {
        var s = s0
        val day = d.day(s.simTimeMs)
        val week = d.week(s.simTimeMs)
        val season = d.season(s.simTimeMs)
        val ready = s.businesses.isNotEmpty() && (s.progress.onboardingStep >= 5 || s.stats.levelsBought >= 10 || s.progress.chapter >= 2)
        if (s.meta.dailyDay != day && ready) {
            val ms = pick(s, p.daily, p.config.dailyCount, "daily", day).map { assign(s, it) }
            s = meta(s) { it.copy(daily = ms, dailyDay = day) }
        }
        if (s.meta.weekIndex != week && ready) {
            val ms = pick(s, p.weekly, p.config.weeklyCount, "weekly", week).map { assign(s, it) }
            s = meta(s) { it.copy(weekly = ms, weekIndex = week, eventGoals = eventGoals(s)) }
        } else if (ready && s.meta.eventGoals.isEmpty()) {
            // The weekly event can begin mid-week (it starts with chapter 3): give it its goals then.
            val goals = eventGoals(s)
            if (goals.isNotEmpty()) s = meta(s) { it.copy(eventGoals = goals) }
        }
        if (s.meta.seasonIndex != season) s = meta(s) { it.copy(seasonIndex = season, seasonXp = 0, seasonClaimedFree = emptySet(), seasonClaimedPremium = emptySet()) }
        // Weekly event goals pay season XP as soon as they are done.
        val goals = s.meta.eventGoals
        if (goals.any { !it.claimed && done(s, it) }) {
            var xp = 0L
            val updated = goals.map { g -> if (!g.claimed && done(s, g)) { xp += g.seasonXp; g.copy(claimed = true) } else g }
            s = meta(s) { it.copy(eventGoals = updated, seasonXp = it.seasonXp + xp) }
        }
        return if (checkAchievements) checkAchievements(s) else s
    }

    private fun eventGoals(s: GameState): List<MissionState> {
        val ev = d.weeklyEvent(s) ?: return emptyList()
        return ev.goals.mapNotNull { metric ->
            p.daily.firstOrNull { it.metric == metric && it.id.endsWith("_normal") }
                ?.let { assign(s, it, 3.0).copy(template = "${ev.id}:$metric", bullion = 0, seasonXp = 60, rewardCash = Money.ZERO) }
        }
    }

    fun claimMission(s: GameState, weekly: Boolean, index: Int, events: MutableList<GameEvent>): Result {
        val list = if (weekly) s.meta.weekly else s.meta.daily
        val m = list.getOrNull(index) ?: return Result.Err(GameError.NotFound("mission"))
        if (m.claimed) return Result.Err(GameError.AlreadyDone)
        if (!done(s, m)) return Result.Err(GameError.Locked("Not finished yet"))
        val ledger = Grants.grant(s.ledger, s.simTimeMs, if (weekly) "weekly mission" else "daily mission", m.rewardCash, m.bullion, 0)
        val nl = list.toMutableList().also { it[index] = m.copy(claimed = true) }
        val key = if (weekly) "weeklies" else "dailies"
        var ns = s.copy(ledger = ledger)
        ns = meta(ns) { mm -> (if (weekly) mm.copy(weekly = nl) else mm.copy(daily = nl)).copy(seasonXp = mm.seasonXp + m.seasonXp, counters = mm.counters + (key to ((mm.counters[key] ?: 0) + 1))) }
        events += GameEvent.MissionClaimed(m.template)
        return Result.Ok(ns)
    }

    fun reroll(s: GameState, index: Int): Result {
        val day = d.day(s.simTimeMs)
        if (s.meta.rerollDay == day) return Result.Err(GameError.Locked("One free reroll a day"))
        val m = s.meta.daily.getOrNull(index) ?: return Result.Err(GameError.NotFound("mission"))
        if (m.claimed) return Result.Err(GameError.AlreadyDone)
        val taken = s.meta.daily.map { p.dailyById[it.template]?.metric }.toSet()
        val pool = p.daily.filter { it.minChapter <= s.progress.chapter && it.metric !in taken }
        if (pool.isEmpty()) return Result.Err(GameError.Invalid("No other mission available"))
        val t = pool[GameRandom.stream(s.worldSeed, "reroll", day).nextInt(pool.size)]
        val nl = s.meta.daily.toMutableList().also { it[index] = assign(s, t) }
        return Result.Ok(meta(s) { it.copy(daily = nl, rerollDay = day) })
    }

    // ----- Daily reward ladder (a missed day pauses it; it never resets) -----

    fun ladderAvailable(s: GameState) = s.meta.ladderLastDay != d.day(s.simTimeMs)

    fun nextLadderStep(s: GameState) = p.ladder.getOrNull((s.meta.ladderClaimed % maxOf(1, p.ladder.size)).toInt())

    fun claimLadder(s: GameState): Result {
        if (!ladderAvailable(s)) return Result.Err(GameError.Locked("Come back tomorrow"))
        val step = nextLadderStep(s) ?: return Result.Err(GameError.NotFound("reward"))
        val cash = economy.totalNetRate(s).over(3600) * Multiplier.ofDouble(step.cashHours * rewardScale(s.progress.chapter))
        val ledger = Grants.grant(s.ledger, s.simTimeMs, "daily reward (day ${step.day})", cash, step.bullion, step.favor)
        return Result.Ok(meta(s.copy(ledger = ledger)) { it.copy(ladderClaimed = it.ladderClaimed + 1, ladderLastDay = d.day(s.simTimeMs), counters = it.counters + ("ladder" to ((it.counters["ladder"] ?: 0) + 1))) })
    }

    // ----- Season track -----

    fun claimSeason(s: GameState, tier: Int, premium: Boolean): Result {
        val t = p.seasonTrack.firstOrNull { it.tier == tier } ?: return Result.Err(GameError.NotFound("tier"))
        if (s.meta.seasonXp < t.xp) return Result.Err(GameError.Locked("Reach ${t.xp} season XP first"))
        if (premium) return Result.Err(GameError.Locked("The premium track comes with the demo Season Pass in the store"))
        if (tier in s.meta.seasonClaimedFree) return Result.Err(GameError.AlreadyDone)
        val r = t.free
        val cash = economy.totalNetRate(s).over(3600) * Multiplier.ofDouble(r.cashHours * rewardScale(s.progress.chapter))
        val ledger = Grants.grant(s.ledger, s.simTimeMs, "season tier $tier", cash, r.bullion, r.favor)
        return Result.Ok(meta(s.copy(ledger = ledger)) { it.copy(seasonClaimedFree = it.seasonClaimedFree + tier) })
    }

    // ----- Achievements -----

    fun achieved(s: GameState, a: AchievementDef): Boolean {
        val g = a.goal
        return when (g.type) {
            "metric" -> counter(s, g.metric) >= g.number
            "netWorth" -> s.stats.peakNetWorth.toDollarsDouble() >= Math.pow(10.0, g.log10.toDouble())
            "chapter" -> content.chapters.firstOrNull { it.number == g.number.toInt() }?.missions?.let { ms -> ms.isNotEmpty() && ms.all { it.id in s.progress.completedMissions } } ?: false
            "business" -> g.id in s.businesses
            "conglomerates" -> s.conglomerates.size >= g.number
            "holding" -> s.holdingName != null
            "boardSeats" -> economy.market.boardSeats(s).size >= g.number
            "properties" -> s.estate.properties.size >= g.number
            "luxuryOwned" -> s.luxury.owned.size >= g.number
            "sets" -> economy.luxury.completedSets(s).size >= g.number
            "statusTier" -> economy.luxury.tierIndex(s) >= g.number
            "fortuneRank" -> economy.world.playerRank(s, s.stats.peakNetWorth) <= g.number
            "rating" -> economy.credit.score(s) >= (content.world.config.finance.ratings.firstOrNull { it.name == g.text }?.min ?: Int.MAX_VALUE)
            "ipo" -> s.dynasty.ipo != null
            "generation" -> s.dynasty.generation >= g.number
            "legacy" -> s.dynasty.legacy >= g.number
            "heirlooms" -> s.dynasty.heirloomsPassed >= g.number
            "challenges" -> s.dynasty.challengesDone.size >= g.number
            "hq" -> s.hq.level >= g.number
            else -> false
        }
    }

    private fun checkAchievements(s: GameState): GameState {
        val have = s.meta.achievements + s.meta.achievementsClaimable
        if (have.size >= p.achievements.size) return s
        val newly = p.achievements.filter { it.id !in have && achieved(s, it) }.map { it.id }
        return if (newly.isEmpty()) s else meta(s) { it.copy(achievementsClaimable = it.achievementsClaimable + newly) }
    }

    fun claimAchievement(s: GameState, id: String): Result {
        val a = p.achievementById[id] ?: return Result.Err(GameError.NotFound("achievement"))
        if (id in s.meta.achievements) return Result.Err(GameError.AlreadyDone)
        if (id !in s.meta.achievementsClaimable) return Result.Err(GameError.Locked("Not unlocked yet"))
        val ledger = Grants.grant(s.ledger, s.simTimeMs, "achievement: ${a.title}", Money.ZERO, a.bullion, 0)
        return Result.Ok(meta(s.copy(ledger = ledger)) { it.copy(achievements = it.achievements + id, achievementsClaimable = it.achievementsClaimable - id) })
    }
}
