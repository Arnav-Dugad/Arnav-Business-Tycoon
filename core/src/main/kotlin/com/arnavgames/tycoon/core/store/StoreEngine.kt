package com.arnavgames.tycoon.core.store

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.ProductDef
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.engine.Grants
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.ModifierState
import com.arnavgames.tycoon.core.state.StoreState

/**
 * The demo store (docs/GDD.md §15). A demo purchase is the simulated result of `SimulatedPurchaseProvider`: it grants
 * the product and marks the player Boosted (leaderboards show it). Bullion purchases use Bullion earned in play and do
 * not mark the player Boosted.
 */
class StoreEngine(private val content: ContentPack) {
    private val c get() = content.store
    private val cfg get() = c.config

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    private fun day(sim: Long) = sim / 86_400_000L

    fun owned(s: GameState, productId: String) = s.store.purchases[productId] ?: 0

    fun vipActive(s: GameState) = s.store.vipUntil > s.simTimeMs

    /** Permanent and VIP income bonus (not the timed ×2 boost, which is a world modifier). */
    fun incomeBonus(s: GameState): Double = s.store.permanentPct + if (vipActive(s)) cfg.vipIncomePct else 0.0

    fun offlineHours(s: GameState): Int = s.store.offlineHours + if (vipActive(s)) cfg.vipOfflineHours else 0

    fun seasonPassActive(s: GameState, season: Long) = season in s.store.seasonPasses

    /** Why a product can't be bought right now (limits and caps), or null. */
    fun blocker(s: GameState, p: ProductDef): String? {
        if (p.limit > 0 && owned(s, p.id) >= p.limit) return "Already owned"
        val g = p.grants
        if (g.permanentPct > 0 && s.store.permanentPct + g.permanentPct > cfg.maxPermanentPct + 1e-9) return "Maximum reached"
        if (g.slots > 0 && s.store.extraSlots + g.slots > cfg.maxSlots) return "Maximum reached"
        if (g.offlineHours > 0 && s.store.offlineHours + g.offlineHours > cfg.maxOfflineHours) return "Maximum reached"
        if (g.cosmetic != null && g.cosmetic in s.store.cosmetics) return "Already owned"
        return null
    }

    fun demoPurchase(s: GameState, productId: String, season: Long, events: MutableList<GameEvent>): Result {
        val p = c.productById[productId] ?: return Result.Err(GameError.NotFound("product"))
        if (p.demoPrice == null) return Result.Err(GameError.Invalid("Bullion only"))
        blocker(s, p)?.let { return Result.Err(GameError.Locked(it)) }
        val ns = grant(s.copy(store = s.store.copy(boosted = true, demoPurchases = s.store.demoPurchases + 1)), p, season)
        events += GameEvent.ProductGranted(p.id, demo = true)
        return Result.Ok(ns)
    }

    fun buyWithBullion(s: GameState, productId: String, season: Long, events: MutableList<GameEvent>): Result {
        val p = c.productById[productId] ?: return Result.Err(GameError.NotFound("product"))
        val price = p.bullionPrice ?: return Result.Err(GameError.Invalid("Not sold for Bullion"))
        blocker(s, p)?.let { return Result.Err(GameError.Locked(it)) }
        val cost = Money.ofCents(price)
        val have = s.ledger.bullion
        if (have < cost) return Result.Err(GameError.InsufficientFunds(cost, have))
        val r = Ledger.post(s.ledger, s.simTimeMs, TxnCategory.PremiumSpend, "store: ${p.name}", listOf(
            Posting(Accounts.BULLION, -cost), Posting(Accounts.WORLD_PREMIUM, cost),
        )) as? Ledger.Result.Ok ?: return Result.Err(GameError.InsufficientFunds(cost, have))
        val ns = grant(s.copy(ledger = r.state), p, season)
        events += GameEvent.ProductGranted(p.id, demo = false)
        return Result.Ok(ns)
    }

    private fun grant(s: GameState, p: ProductDef, season: Long): GameState {
        val g = p.grants
        var ns = s
        if (g.bullion > 0 || g.favor > 0) ns = ns.copy(ledger = Grants.grant(ns.ledger, ns.simTimeMs, "store: ${p.name}", Money.ZERO, g.bullion, g.favor))
        if (g.boostHours > 0) {
            // A new boost extends the running one rather than stacking multipliers.
            val current = ns.world.modifiers.firstOrNull { it.source == BOOST && it.untilSim > ns.simTimeMs }
            val until = (current?.untilSim ?: ns.simTimeMs) + g.boostHours * 3_600_000L
            val mods = ns.world.modifiers.filterNot { it.source == BOOST } + ModifierState(BOOST, "all", cfg.boostPct, until, "Income boost")
            ns = ns.copy(world = ns.world.copy(modifiers = mods))
        }
        var st = ns.store
        st = st.copy(purchases = st.purchases + (p.id to (st.purchases[p.id] ?: 0) + 1))
        if (g.permanentPct > 0) st = st.copy(permanentPct = st.permanentPct + g.permanentPct)
        if (g.slots > 0) st = st.copy(extraSlots = st.extraSlots + g.slots)
        if (g.offlineHours > 0) st = st.copy(offlineHours = st.offlineHours + g.offlineHours)
        if (g.vipDays > 0) st = st.copy(vipUntil = maxOf(st.vipUntil, ns.simTimeMs) + g.vipDays * 86_400_000L)
        if (g.seasonPass > 0) st = st.copy(seasonPasses = st.seasonPasses + season)
        g.cosmetic?.let { st = st.copy(cosmetics = st.cosmetics + it) }
        return ns.copy(store = st)
    }

    fun equip(s: GameState, cosmeticId: String?, kind: String): Result {
        if (cosmeticId != null) {
            val def = c.cosmeticById[cosmeticId] ?: return Result.Err(GameError.NotFound("cosmetic"))
            if (cosmeticId !in s.store.cosmetics) return Result.Err(GameError.Locked("Not owned"))
            if (def.kind != kind) return Result.Err(GameError.Invalid("Wrong kind"))
        }
        return Result.Ok(s.copy(store = if (kind == "title") s.store.copy(title = cosmeticId) else s.store.copy(frame = cosmeticId)))
    }

    /** Next VIP expiry after [now] (an engine boundary: the income bonus changes there). */
    fun nextBoundary(s: GameState, now: Long): Long? = s.store.vipUntil.takeIf { it > now }

    companion object {
        const val BOOST = "store_boost"
    }
}
