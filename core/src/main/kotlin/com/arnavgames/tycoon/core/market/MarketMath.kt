package com.arnavgames.tycoon.core.market

import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.core.content.MarketContent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.Qty
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

/** Pure read-only market calculations shared by the engine, the economy (net worth, perks) and the UI. */
class MarketMath(val m: MarketContent) {
    private val cfg = m.config

    fun depthScale(chapter: Int): Double = cfg.depthScaleByChapter.getOrElse((chapter - 1).coerceAtLeast(0)) { cfg.depthScaleByChapter.lastOrNull() ?: 1.0 }

    fun priceMicros(state: GameState, id: String): Long? {
        val q = state.market.quotes[id] ?: return null
        return toMicros(exp(q.logV + q.x))
    }

    fun toMicros(price: Double): Long = (price * 1e6).roundToLong().coerceAtLeast(1)

    fun assetClass(id: String): AssetClass? = m.instrumentById[id]?.assetClass

    /** Market depth in dollars: how big an order must be to move the fill price noticeably. */
    fun depthDollars(state: GameState, id: String): Double {
        val scale = depthScale(state.progress.chapter)
        return when (assetClass(id)) {
            AssetClass.Stock -> m.stockById.getValue(id).baseCapBillions * 1e9 * scale
            AssetClass.Fund -> m.stocks.sumOf { it.baseCapBillions } * 1e9 * scale * cfg.fundDepthMultiplier / m.stocks.size.coerceAtLeast(1)
            AssetClass.Bond -> cfg.bondDepthBillions * 1e9 * scale
            AssetClass.Crypto -> m.coinById.getValue(id).baseCapBillions * 1e9 * scale
            AssetClass.Commodity -> cfg.commodityDepthBillions * 1e9 * scale
            null -> 1e9
        }
    }

    /** Average fill penalty for an order of [orderDollars]: k · (order / depth)^0.6, capped (docs/ECONOMY.md §5.1). */
    fun slippage(state: GameState, id: String, orderDollars: Double): Double {
        if (orderDollars <= 0) return 0.0
        val ratio = orderDollars / depthDollars(state, id)
        return (cfg.slippageK * ratio.pow(cfg.slippageExponent)).coerceAtMost(cfg.slippageMax)
    }

    /** Shares outstanding for a stock at the current chapter's depth (companies issue shares as markets deepen). */
    fun outstanding(state: GameState, stockId: String): Qty {
        val s = m.stockById.getValue(stockId)
        val shares = BigDecimal(s.baseCapBillions * 1e9 * depthScale(state.progress.chapter))
            .divide(BigDecimal(s.startPrice), 6, RoundingMode.DOWN)
        return Qty(shares.movePointRight(6).setScale(0, RoundingMode.DOWN).toBigInteger().max(BigInteger.ONE))
    }

    /** Fraction of a company the player owns (a double: only compared against thresholds and shown rounded). */
    fun stake(state: GameState, stockId: String): Double {
        val h = state.market.holdings[stockId] ?: return 0.0
        val s = m.stockById[stockId] ?: return 0.0
        val outstandingShares = s.baseCapBillions * 1e9 * depthScale(state.progress.chapter) / s.startPrice.toDouble()
        return h.qty.toDouble() / outstandingShares
    }

    fun boardSeats(state: GameState): List<String> =
        state.market.holdings.keys.filter { it in m.stockById && stake(state, it) >= cfg.boardSeatStake }.sorted()

    /** Sectors in which the player holds at least the board-seat stake in some company. */
    fun seatSectors(state: GameState): Set<String> = boardSeats(state).map { m.stockById.getValue(it).sector }.toSet()

    fun divisionSectors(state: GameState): Set<String> =
        state.market.holdings.keys.filter { it in m.stockById && stake(state, it) >= cfg.divisionStake }.map { m.stockById.getValue(it).sector }.toSet()

    fun holdingValue(state: GameState, id: String): Money {
        val h = state.market.holdings[id] ?: return Money.ZERO
        val p = priceMicros(state, id) ?: return Money.ZERO
        return h.qty.valueAt(p)
    }

    fun portfolioValue(state: GameState): Money {
        var total = Money.ZERO
        for (id in state.market.holdings.keys) total += holdingValue(state, id)
        return total
    }

    /** Current cost to buy back a short position. */
    fun shortLiability(state: GameState, id: String): Money {
        val sp = state.market.shorts[id] ?: return Money.ZERO
        val p = priceMicros(state, id) ?: return Money.ZERO
        return sp.qty.valueAt(p)
    }

    /** Equity locked in shorts: collateral minus what it would cost to cover (never below zero: losses are capped). */
    fun shortEquity(state: GameState): Money {
        var total = Money.ZERO
        for ((id, sp) in state.market.shorts) total += (sp.collateral - shortLiability(state, id)).max(Money.ZERO)
        return total
    }

    fun isUnlocked(state: GameState, feature: String): Boolean = state.progress.chapter >= (cfg.unlocks[feature] ?: 99)

    fun unlockChapter(feature: String): Int = cfg.unlocks[feature] ?: 99

    fun featureOf(ac: AssetClass): String = when (ac) {
        AssetClass.Stock -> "stocks"
        AssetClass.Fund -> "funds"
        AssetClass.Bond -> "bonds"
        AssetClass.Crypto -> "crypto"
        AssetClass.Commodity -> "commodities"
    }

    /** Day-over-day change, from the oldest fine history point (24 h ago when the history is full). */
    fun change24h(state: GameState, id: String): Double {
        val q = state.market.quotes[id] ?: return 0.0
        val old = q.fine.firstOrNull()?.let { com.arnavgames.tycoon.core.state.PriceHistory.price(it) } ?: return 0.0
        return if (old > 0) q.price / old - 1.0 else 0.0
    }
}
