package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.Serializable

/**
 * The player's real estate (docs/GDD.md §7). District price indices and listings are pure functions of the world
 * seed and time, so only what the player owns or changed is stored here.
 */
@Serializable
data class EstateState(
    val properties: Map<Long, PropertyState> = emptyMap(),
    val nextPropertyId: Long = 1,
    /** Listings already bought in the current listing epoch ("district:epoch:index"). */
    val boughtListings: Set<String> = emptySet(),
    /** Permanent district price boosts from gentrification and landmarks (log units). */
    val districtBoost: Map<String, Double> = emptyMap(),
    /** Tenant decisions waiting for the player (never auto-resolved). */
    val events: List<TenantEventState> = emptyList(),
    val nextEventId: Long = 1,
    /** Business id → property it operates from (pays no rent). */
    val hosted: Map<String, Long> = emptyMap(),
    /** Minute index of the last processed estate tick. */
    val tick: Long = -1,
)

@Serializable
data class PropertyState(
    val id: Long,
    val district: String,
    val type: String,
    /** Floor-area multiplier (0.6–2.2): bigger means pricier and higher rent. */
    val size: Double,
    /** Condition in permille (200–1000); a disaster or neglect lowers it, renovation or repair restores it. */
    val condition: Int,
    val renovations: Int = 0,
    /** Asking rent as a percentage of market rent. */
    val rentPct: Int = 100,
    /** Sim time at which the current tenant moves in (rent is paid only once they have). */
    val tenantFrom: Long = 0,
    val costBasis: Money,
    val mortgage: Money = Money.ZERO,
    val insured: Boolean = false,
    /** Business operating from this property (it then pays no rent to you or anyone else). */
    val hostedBusiness: String? = null,
    val tower: TowerState? = null,
    /** Rent ×1.1 (or ×0.9 when negative) until this sim time, from tenant decisions. */
    val boostUntil: Long = 0,
    val boostSign: Int = 0,
    val boughtAtSim: Long = 0,
)

@Serializable
data class TowerState(val design: String, val stagesDone: Int = 0, val completedAtSim: Long = 0)

@Serializable
data class TenantEventState(val id: Long, val propertyId: Long, val event: String, val createdSim: Long)
