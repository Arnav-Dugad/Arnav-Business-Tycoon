package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

/** Every player action. Commands are validated by the engine and either applied fully or rejected with a typed error. */
sealed interface Command {
    data class OpenBusiness(val businessId: String) : Command
    /** [count] = -1 buys the maximum affordable. */
    data class BuyLevels(val businessId: String, val count: Int) : Command
    data class Collect(val businessId: String) : Command
    data object CollectAll : Command
    data class HireManager(val businessId: String, val managerId: String) : Command
    data class StartExpansion(val businessId: String, val expansionId: String) : Command
    data class RushTimer(val timerId: Long) : Command
    data class HustleTap(val taps: Int = 1) : Command
    data object ActivateFrenzy : Command
    data object UpgradeHq : Command
    data class SetMothballed(val businessId: String, val mothballed: Boolean) : Command
    data class ClaimMission(val missionId: String) : Command
    data class AdvanceOnboarding(val toStep: Int) : Command

    // Mechanic decisions
    data class SetRetailPrice(val businessId: String, val pricePct: Int) : Command
    data class StartRetailPromo(val businessId: String) : Command
    data class SetRestaurantQuality(val businessId: String, val tierId: String) : Command
    data class AssignVehicles(val businessId: String, val routeId: String, val count: Int) : Command
    data class AutoPlanRoutes(val businessId: String) : Command
}

sealed interface GameError {
    data class InsufficientFunds(val needed: Money, val available: Money) : GameError
    data class InsufficientFavor(val needed: Long, val available: Long) : GameError
    data object NoFreeSlot : GameError
    data class Locked(val reason: String) : GameError
    data class NotFound(val what: String) : GameError
    data class Invalid(val reason: String) : GameError
    data object AlreadyDone : GameError
}

/** Things that happened, used by the UI for moments, haptics, sounds and toasts. */
sealed interface GameEvent {
    data class BusinessOpened(val businessId: String) : GameEvent
    data class LevelsBought(val businessId: String, val count: Int, val newLevel: Int, val cost: Money) : GameEvent
    data class TierReached(val businessId: String, val tierName: String, val level: Int) : GameEvent
    data class Collected(val businessId: String, val amount: Money) : GameEvent
    data class ManagerHired(val businessId: String, val managerId: String) : GameEvent
    data class ExpansionStarted(val businessId: String, val expansionId: String, val endsAt: Long) : GameEvent
    data class ExpansionCompleted(val businessId: String, val expansionId: String) : GameEvent
    data class HustleEarned(val amount: Money, val combo: Int) : GameEvent
    data class FrenzyStarted(val until: Long) : GameEvent
    data class HqUpgraded(val level: Int) : GameEvent
    data class MissionCompleted(val missionId: String) : GameEvent
    data class MissionClaimed(val missionId: String) : GameEvent
    data class ChapterCompleted(val chapter: Int) : GameEvent
    data class StorageFull(val businessId: String) : GameEvent
    data class PromoEnded(val businessId: String) : GameEvent
}

sealed interface CommandResult {
    data class Ok(val state: GameState, val events: List<GameEvent>) : CommandResult
    data class Rejected(val error: GameError) : CommandResult
}

data class AdvanceResult(val state: GameState, val events: List<GameEvent>, val earned: Money)
