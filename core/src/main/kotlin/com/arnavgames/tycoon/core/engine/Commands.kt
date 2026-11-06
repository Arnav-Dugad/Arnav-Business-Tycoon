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

    /** A decision or investment in a module mechanic (see core.engine.mechanics). */
    data class MechanicAction(val businessId: String, val action: String, val value: String = "") : Command

    // Merger tree
    data class FormConglomerate(val conglomerateId: String) : Command
    data class FormHolding(val name: String) : Command
    // Markets (docs/GDD.md §6)
    data class BuyInstrument(val instrumentId: String, val amount: Money) : Command
    /** Sells [permille]/1000 of a position (1000 = everything). */
    data class SellInstrument(val instrumentId: String, val permille: Int) : Command
    data class OpenShort(val stockId: String, val amount: Money) : Command
    data class CoverShort(val stockId: String) : Command
    // Real estate (docs/GDD.md §7)
    data class BuyProperty(val listingId: String, val ltv: Double = 0.0, val insured: Boolean = false) : Command
    data class SetRent(val propertyId: Long, val rentPct: Int) : Command
    data class RenovateProperty(val propertyId: Long, val repair: Boolean = false) : Command
    data class SellProperty(val propertyId: Long) : Command
    data class CancelPropertySale(val propertyId: Long) : Command
    data class SetInsured(val propertyId: Long, val insured: Boolean) : Command
    data class RepayMortgage(val propertyId: Long) : Command
    /** Run a business from this property (null = stop and rent it out again). */
    data class HostBusiness(val propertyId: Long, val businessId: String?) : Command
    /** Starts the next construction stage of a skyscraper (the design is fixed by the first stage). */
    data class BuildTower(val propertyId: Long, val designId: String) : Command
    data class ResolveTenantEvent(val eventId: Long, val choice: Int) : Command
    // Luxury (docs/GDD.md §8)
    data class BuyLuxury(val itemId: String) : Command
    data class SellLuxury(val itemId: String) : Command
    data class UpgradeShowroom(val showroomId: String) : Command
    /** Bids the next increment in the live auction. */
    data object BidAuction : Command
    /** Leaves a proxy maximum: the game bids for you up to this amount, even while you are away. */
    data class SetMaxBid(val max: Money) : Command
    // World, rivals and finance (docs/GDD.md §9–11)
    /** [edge] (−0.15..0.15) comes from the Negotiation mini-game: it shifts the odds of the option's first (best) outcome. */
    data class ResolveCard(val cardId: Long, val option: Int, val edge: Double = 0.0) : Command
    data class TakeLoan(val amount: Money, val days: Int) : Command
    data class RepayLoan(val loanId: Long) : Command
    data class DrawCredit(val amount: Money) : Command
    data class RepayCredit(val amount: Money) : Command
    /** Longer loan terms when debt service exceeds income (the rating drops). */
    data object Restructure : Command
    /** Orderly liquidation when net worth has been negative for a while (GDD §11). */
    data object AcceptPhoenix : Command
    /** Files the pending tax quarter yourself, claiming earned deductions (optionally an aggressive interpretation). */
    data class FileTaxes(val aggressive: Boolean) : Command
    data class SetAutoFile(val on: Boolean) : Command
    // Progression and the Dynasty (docs/GDD.md §12)
    data class ClaimDaily(val index: Int) : Command
    data class ClaimWeekly(val index: Int) : Command
    data class RerollDaily(val index: Int) : Command
    data class ClaimAchievement(val id: String) : Command
    data object ClaimDailyReward : Command
    data class ClaimSeasonTier(val tier: Int, val premium: Boolean = false) : Command
    /** Retire the founder: the heir starts over with Legacy, the chosen trait, heirlooms, charter and challenge. */
    data class PassTheCrown(val trait: String, val heirlooms: List<String>, val challenge: String?, val charter: Set<String>) : Command
    data class LaunchIpo(val float: Double) : Command
    // Demo store (docs/GDD.md §15): only SimulatedPurchaseProvider issues DemoPurchase, after its DEMO sheet.
    data class DemoPurchase(val productId: String) : Command
    data class BuyWithBullion(val productId: String) : Command
    data class EquipCosmetic(val cosmeticId: String?, val kind: String) : Command
    // Social (docs/GDD.md §14): applied by the client after Firestore confirms (rules cap the amounts).
    data class ReceiveGift(val favor: Long) : Command
    /** The player won this week's friends' Sealed Lot: pay the winning bid and take the piece. */
    data class WinSealedLot(val itemId: String, val price: Money) : Command
    /** Market Timing mini-game (GDD §13): a practice run on a replayed price tape; score 0–100 pays 1–10 Favor once a day. */
    data class FinishMarketTiming(val score: Int) : Command
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
    data class ConglomerateFormed(val conglomerateId: String) : GameEvent
    data class HoldingFormed(val name: String) : GameEvent
    data class TradeExecuted(val instrumentId: String, val buy: Boolean, val value: Money) : GameEvent
    data class ShortCovered(val stockId: String, val profit: Money, val forced: Boolean) : GameEvent
    data class MarketNews(val newsId: Long) : GameEvent
    data class EarningsSeason(val beats: Int, val misses: Int) : GameEvent
    data class MarketPayout(val amount: Money) : GameEvent
    data class PropertyBought(val propertyId: Long, val price: Money) : GameEvent
    data class PropertySold(val propertyId: Long, val net: Money, val gain: Money) : GameEvent
    data class RenovationStarted(val propertyId: Long, val endsAt: Long) : GameEvent
    data class RenovationCompleted(val propertyId: Long) : GameEvent
    data class SaleListed(val propertyId: Long, val endsAt: Long) : GameEvent
    data class TowerStageStarted(val propertyId: Long, val stage: Int, val endsAt: Long) : GameEvent
    data class TowerStageCompleted(val propertyId: Long, val stagesDone: Int) : GameEvent
    data class TowerCompleted(val propertyId: Long) : GameEvent
    data class Disaster(val propertyId: Long, val insured: Boolean) : GameEvent
    data class Foreclosure(val propertyId: Long) : GameEvent
    data class TenantEventRaised(val propertyId: Long) : GameEvent
    data class TenantDecision(val eventId: Long) : GameEvent
    data class LuxuryBought(val itemId: String, val price: Money) : GameEvent
    data class LuxurySold(val itemId: String, val proceeds: Money) : GameEvent
    data class AuctionStarted(val itemId: String) : GameEvent
    data class AuctionBid(val itemId: String, val amount: Money) : GameEvent
    data class AuctionWon(val itemId: String, val price: Money) : GameEvent
    data class AuctionLost(val itemId: String, val winner: String) : GameEvent
    data class CardDrawn(val cardId: Long) : GameEvent
    data class CardResolved(val cardId: Long, val title: String, val result: String) : GameEvent
    data class WorldEventStarted(val eventId: String) : GameEvent
    data class BusinessSold(val businessId: String, val price: Money) : GameEvent
    data class NemesisBeat(val chapter: Int, val beat: Int) : GameEvent
    data class PaymentMissed(val source: String, val amount: Money) : GameEvent
    data class LoanTaken(val loanId: Long, val amount: Money) : GameEvent
    data class LoanRepaid(val loanId: Long) : GameEvent
    data class QuarterEnded(val quarter: Long) : GameEvent
    data class TaxFiled(val refund: Money, val audited: Boolean, val auto: Boolean) : GameEvent
    data object Restructured : GameEvent
    data object PhoenixReborn : GameEvent
    data class CrownPassed(val generation: Int, val legacyGained: Long) : GameEvent
    data class IpoLaunched(val raised: Money) : GameEvent
    data class ProductGranted(val productId: String, val demo: Boolean) : GameEvent
}

sealed interface CommandResult {
    data class Ok(val state: GameState, val events: List<GameEvent>) : CommandResult
    data class Rejected(val error: GameError) : CommandResult
}

data class AdvanceResult(val state: GameState, val events: List<GameEvent>, val earned: Money)
