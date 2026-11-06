package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.GoalDef
import com.arnavgames.tycoon.core.content.MissionDef
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

/** Story chapter missions: evaluated from state, completed automatically, rewards claimed by the player. */
internal class Missions(private val content: ContentPack, private val economy: Economy) {

    fun currentChapter(s: GameState) = content.chapters.firstOrNull { it.number == s.progress.chapter }

    fun isMet(s: GameState, goal: GoalDef): Boolean = when (goal) {
        is GoalDef.OwnBusiness -> goal.businessId in s.businesses
        is GoalDef.BusinessLevel -> (s.businesses[goal.businessId]?.level ?: 0) >= goal.level
        is GoalDef.HireManager -> s.businesses[goal.businessId]?.managerId != null
        is GoalDef.NetWorth -> economy.netWorth(s) >= goal.amount
        is GoalDef.IncomePerSecond -> economy.totalNetRate(s).perSecond() >= goal.amount
        is GoalDef.HustleTaps -> s.hustle.totalTaps >= goal.count
        is GoalDef.ExpansionDone -> s.businesses[goal.businessId]?.expansionsDone?.contains(goal.expansionId) == true
        is GoalDef.HqLevel -> s.hq.level >= goal.level
        is GoalDef.Conglomerates -> s.conglomerates.size >= goal.count
        is GoalDef.BusinessesOwned -> s.businesses.size >= goal.count
        is GoalDef.MarketTrades -> s.market.trades >= goal.count
        is GoalDef.BoardSeats -> economy.market.boardSeats(s).size >= goal.count
    }

    /** Progress toward a goal in [0, 1] for UI bars. */
    fun progress(s: GameState, goal: GoalDef): Double = when (goal) {
        is GoalDef.BusinessLevel -> ((s.businesses[goal.businessId]?.level ?: 0).toDouble() / goal.level).coerceIn(0.0, 1.0)
        is GoalDef.NetWorth -> economy.netWorth(s).ratioTo(goal.amount).coerceIn(0.0, 1.0)
        is GoalDef.IncomePerSecond -> economy.totalNetRate(s).perSecond().ratioTo(goal.amount).coerceIn(0.0, 1.0)
        is GoalDef.HustleTaps -> (s.hustle.totalTaps.toDouble() / goal.count).coerceIn(0.0, 1.0)
        is GoalDef.HqLevel -> (s.hq.level.toDouble() / goal.level).coerceIn(0.0, 1.0)
        is GoalDef.Conglomerates -> (s.conglomerates.size.toDouble() / goal.count).coerceIn(0.0, 1.0)
        is GoalDef.BusinessesOwned -> (s.businesses.size.toDouble() / goal.count).coerceIn(0.0, 1.0)
        is GoalDef.MarketTrades -> (s.market.trades.toDouble() / goal.count).coerceIn(0.0, 1.0)
        is GoalDef.BoardSeats -> (economy.market.boardSeats(s).size.toDouble() / goal.count).coerceIn(0.0, 1.0)
        else -> if (isMet(s, goal)) 1.0 else 0.0
    }

    fun evaluate(s: GameState, events: MutableList<GameEvent>): GameState {
        val chapter = currentChapter(s) ?: return s
        val newlyDone = chapter.missions.filter { it.id !in s.progress.completedMissions && isMet(s, it.goal) }
        if (newlyDone.isEmpty()) return s
        newlyDone.forEach { events += GameEvent.MissionCompleted(it.id) }
        val ids = newlyDone.map { it.id }.toSet()
        return s.copy(
            progress = s.progress.copy(
                completedMissions = s.progress.completedMissions + ids,
                claimableMissions = s.progress.claimableMissions + ids,
            ),
        )
    }

    fun claim(s: GameState, missionId: String, events: MutableList<GameEvent>): GameEngine.Either {
        if (missionId !in s.progress.claimableMissions) return GameEngine.Either.Err(GameError.Invalid("Mission not ready"))
        val mission: MissionDef = content.chapters.flatMap { it.missions }.first { it.id == missionId }
        val ledger = Grants.grant(s.ledger, s.simTimeMs, "mission ${mission.id}", mission.rewardCash, mission.rewardBullion, mission.rewardFavor)
        events += GameEvent.MissionClaimed(missionId)
        var ns = s.copy(ledger = ledger, progress = s.progress.copy(claimableMissions = s.progress.claimableMissions - missionId))
        // Chapter complete when every mission is completed and claimed.
        val chapter = currentChapter(ns)
        if (chapter != null && chapter.missions.all { it.id in ns.progress.completedMissions } &&
            chapter.missions.none { it.id in ns.progress.claimableMissions } &&
            content.chapters.any { it.number == chapter.number + 1 }
        ) {
            events += GameEvent.ChapterCompleted(chapter.number)
            ns = ns.copy(progress = ns.progress.copy(chapter = chapter.number + 1))
        }
        return GameEngine.Either.Ok(ns)
    }
}
