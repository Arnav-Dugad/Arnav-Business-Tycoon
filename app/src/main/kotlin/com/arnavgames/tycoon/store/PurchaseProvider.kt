package com.arnavgames.tycoon.store

import com.arnavgames.tycoon.core.content.ProductDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.game.GameSession

/** Result of a purchase attempt. */
sealed interface PurchaseResult {
    data object Granted : PurchaseResult
    data class Refused(val reason: String) : PurchaseResult
}

/**
 * Every purchase goes through this interface (docs/ARCHITECTURE.md §8). The only implementation shipped is
 * [SimulatedPurchaseProvider]: there is no Play Billing dependency, no payment UI and no payment data.
 */
interface PurchaseProvider {
    fun catalog(): List<ProductDef>
    /** Called only after the player confirmed the DEMO PURCHASE sheet. */
    suspend fun purchase(productId: String): PurchaseResult
    /** Entitlements already owned (they live in the save, so restoring is reading it). */
    fun restore(): Map<String, Int>
}

/**
 * Simulated purchases: the UI shows a clearly labelled "DEMO PURCHASE — no real money is charged" sheet; on confirm
 * this grants the product through the engine (which marks the player Boosted). Nothing is charged or collected.
 */
class SimulatedPurchaseProvider(private val session: GameSession) : PurchaseProvider {
    override fun catalog(): List<ProductDef> = session.engine.content.store.products

    override suspend fun purchase(productId: String): PurchaseResult = when (val r = session.dispatch(Command.DemoPurchase(productId))) {
        is CommandResult.Ok -> PurchaseResult.Granted
        is CommandResult.Rejected -> PurchaseResult.Refused(r.error.toString())
    }

    override fun restore(): Map<String, Int> = session.state.value?.store?.purchases ?: emptyMap()

    companion object {
        const val DEMO_LABEL = "DEMO PURCHASE — no real money is charged"
    }
}
