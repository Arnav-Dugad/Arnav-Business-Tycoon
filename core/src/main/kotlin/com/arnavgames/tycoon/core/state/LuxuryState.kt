package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.Serializable

/** The player's collection, showrooms and the live auction (docs/GDD.md §8). */
@Serializable
data class LuxuryState(
    val owned: Map<String, OwnedItem> = emptyMap(),
    /** Showroom id → upgrade level (0 = base capacity). */
    val showroomLevels: Map<String, Int> = emptyMap(),
    val auction: AuctionState? = null,
    /** Auction slot index of the last auction that was scheduled (one per auctionEveryHours). */
    val lastAuctionSlot: Long = -1,
    val auctionsWon: Int = 0,
    val auctionsLost: Int = 0,
    /** Items chosen to survive a Dynasty prestige (used from Phase 10). */
    val heirlooms: Set<String> = emptySet(),
    val tick: Long = -1,
)

@Serializable
data class OwnedItem(val paid: Money, val boughtAtSim: Long, val viaAuction: Boolean = false)

/**
 * A live auction. Rival bids happen at seeded moments ([nextBidAt]); each bid pushes the close time out to at least
 * [extend] seconds away, so the auction ends only after a quiet spell.
 */
@Serializable
data class AuctionState(
    val slot: Long,
    val item: String,
    val startSim: Long,
    val closeSim: Long,
    val bid: Money,
    /** "player", a bidder id, or "" before the first bid. */
    val leader: String = "",
    val bidders: List<AuctionBidder>,
    val bidCount: Int = 0,
    val log: List<String> = emptyList(),
    /** The player's proxy maximum: the game bids for them up to this amount, even while they are away. */
    val playerMax: Money = Money.ZERO,
)

@Serializable
data class AuctionBidder(val id: String, val maxBid: Money, val nextBidAt: Long, val out: Boolean = false)
