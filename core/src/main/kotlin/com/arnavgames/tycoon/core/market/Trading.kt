package com.arnavgames.tycoon.core.market

import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.Holding
import com.arnavgames.tycoon.core.state.Qty
import com.arnavgames.tycoon.core.state.ShortPosition
import java.math.BigInteger

/**
 * Orders at the current quote (docs/ECONOMY.md §5.1). Every order pays slippage and a fee; realised gains pay
 * capital-gains tax. Orders never move the quoted price (the market absorbs them), so quotes stay a pure function
 * of the world seed and the minute.
 *
 * Ledger: buying moves cash into `asset:investments` at cost; selling releases that cost basis and books the
 * difference to `revenue:capital_gains`. Shorts lock proceeds plus margin in `asset:collateral` against
 * `liability:shorts`; a cover can never take more than the collateral (the broker absorbs any gap), so a short can
 * never create an unbounded debt.
 */
class Trading(private val engine: MarketEngine) {
    private val math = engine.math
    private val cfg = math.m.config

    sealed interface Result {
        data class Ok(val state: GameState, val preview: OrderPreview) : Result
        data class Err(val error: GameError) : Result
    }

    /** What an order would do, for the confirmation UI (also returned by executed orders). */
    data class OrderPreview(
        val quantity: Qty,
        val gross: Money,
        val fee: Money,
        val slippage: Double,
        val fillPriceMicros: Long,
        val tax: Money = Money.ZERO,
        val net: Money = gross,
        val realizedGain: Money = Money.ZERO,
    )

    private fun err(e: GameError) = Result.Err(e)

    private fun gate(state: GameState, id: String): GameError? {
        val ac = math.assetClass(id) ?: return GameError.NotFound("instrument $id")
        val feature = math.featureOf(ac)
        if (!math.isUnlocked(state, feature)) return GameError.Locked("Unlocks in chapter ${math.unlockChapter(feature)}")
        if (state.market.quotes[id] == null) return GameError.Locked("The market has not opened yet")
        return null
    }

    // ----- Buy -----

    fun previewBuy(state: GameState, id: String, amount: Money): Result {
        gate(state, id)?.let { return err(it) }
        if (amount < Money.ofDollars(1)) return err(GameError.Invalid("Minimum order is \$1"))
        val price = math.priceMicros(state, id)!!
        val slip = math.slippage(state, id, amount.toDollarsDouble())
        val fee = amount * Multiplier.ofDouble(cfg.feeRate)
        val fill = math.toMicros(price / 1e6 * (1 + slip))
        val qty = Qty.affordable(amount - fee, fill)
        if (!qty.isPositive) return err(GameError.Invalid("Order too small"))
        if (math.assetClass(id) == AssetClass.Stock) {
            val held = state.market.holdings[id]?.qty ?: Qty.ZERO
            val max = math.outstanding(state, id).fraction((cfg.maxStake * 1_000_000).toLong(), 1_000_000)
            if (held + qty > max) return err(GameError.Invalid("You can own at most ${(cfg.maxStake * 100).toInt()}% of a listed company"))
        }
        return Result.Ok(state, OrderPreview(qty, amount, fee, slip, fill, net = amount - fee))
    }

    fun buy(state: GameState, id: String, amount: Money, events: MutableList<GameEvent>): Result {
        val p = previewBuy(state, id, amount)
        if (p !is Result.Ok) return p
        val pv = p.preview
        val posted = Ledger.post(
            state.ledger, state.simTimeMs, TxnCategory.Investment, "buy ${engine.tickerOf(id)}",
            listOf(Posting(Accounts.CASH, -amount), Posting(MarketEngine.INVESTMENTS, pv.net), Posting(Accounts.expense("market_fees"), pv.fee)),
        )
        val ledger = when (posted) {
            is Ledger.Result.Ok -> posted.state
            is Ledger.Result.Err -> return err(insufficient(posted.error))
        }
        val old = state.market.holdings[id]
        val h = Holding((old?.qty ?: Qty.ZERO) + pv.quantity, (old?.costBasis ?: Money.ZERO) + pv.net)
        events += GameEvent.TradeExecuted(id, buy = true, value = amount)
        return Result.Ok(
            state.copy(ledger = ledger, market = state.market.copy(holdings = state.market.holdings + (id to h), trades = state.market.trades + 1)),
            pv,
        )
    }

    // ----- Sell -----

    /** Sells [permille]/1000 of the position. */
    fun previewSell(state: GameState, id: String, permille: Int): Result {
        gate(state, id)?.let { return err(it) }
        if (permille !in 1..1000) return err(GameError.Invalid("permille"))
        val h = state.market.holdings[id] ?: return err(GameError.NotFound("You hold no $id"))
        val qty = if (permille == 1000) h.qty else h.qty.fraction(permille.toLong(), 1000)
        if (!qty.isPositive) return err(GameError.Invalid("Order too small"))
        val price = math.priceMicros(state, id)!!
        val marketValue = qty.valueAt(price)
        val slip = math.slippage(state, id, marketValue.toDollarsDouble())
        val fill = math.toMicros(price / 1e6 * (1 - slip))
        val gross = qty.valueAt(fill)
        val fee = gross * Multiplier.ofDouble(cfg.feeRate)
        val basis = if (permille == 1000) h.costBasis else basisPart(h, qty)
        val gain = gross - basis
        val taxable = gross - fee - basis
        val tax = if (taxable.isPositive) taxable * Multiplier.ofDouble(cfg.capitalGainsTax) else Money.ZERO
        return Result.Ok(state, OrderPreview(qty, gross, fee, slip, fill, tax, gross - fee - tax, gain))
    }

    fun sell(state: GameState, id: String, permille: Int, events: MutableList<GameEvent>): Result {
        val p = previewSell(state, id, permille)
        if (p !is Result.Ok) return p
        val pv = p.preview
        val h = state.market.holdings.getValue(id)
        val basis = pv.gross - pv.realizedGain
        val posted = Ledger.post(
            state.ledger, state.simTimeMs, TxnCategory.Investment, "sell ${engine.tickerOf(id)}",
            listOf(
                Posting(Accounts.CASH, pv.net),
                Posting(MarketEngine.INVESTMENTS, -basis),
                Posting(Accounts.expense("market_fees"), pv.fee),
                Posting(Accounts.expense("tax"), pv.tax),
                Posting(Accounts.revenue("capital_gains"), -pv.realizedGain),
            ),
            floors = emptyMap(),
        ) as Ledger.Result.Ok
        val left = h.qty - pv.quantity
        val holdings = if (left.isPositive) state.market.holdings + (id to Holding(left, h.costBasis - basis)) else state.market.holdings - id
        events += GameEvent.TradeExecuted(id, buy = false, value = pv.net)
        return Result.Ok(state.copy(ledger = posted.state, market = state.market.copy(holdings = holdings, trades = state.market.trades + 1)), pv)
    }

    private fun basisPart(h: Holding, qty: Qty): Money =
        Money.ofCents(h.costBasis.cents.multiply(qty.micro).divide(h.qty.micro.max(BigInteger.ONE)))

    // ----- Shorts -----

    fun previewShort(state: GameState, id: String, amount: Money): Result {
        if (math.assetClass(id) != AssetClass.Stock) return err(GameError.Invalid("Only stocks can be sold short"))
        gate(state, id)?.let { return err(it) }
        if (!math.isUnlocked(state, "shorts")) return err(GameError.Locked("Short selling unlocks in chapter ${math.unlockChapter("shorts")}"))
        if (id in state.market.shorts) return err(GameError.Invalid("Cover your open short first"))
        if (id in state.market.holdings) return err(GameError.Invalid("Sell your shares before shorting this company"))
        if (amount < Money.ofDollars(10)) return err(GameError.Invalid("Minimum short is \$10"))
        val price = math.priceMicros(state, id)!!
        val slip = math.slippage(state, id, amount.toDollarsDouble())
        val fill = math.toMicros(price / 1e6 * (1 - slip))
        val qty = Qty.affordable(amount, fill)
        val fee = amount * Multiplier.ofDouble(cfg.feeRate)
        // The player puts up margin on top of the proceeds: collateral = proceeds × initial margin.
        val margin = amount * Multiplier.ofDouble(cfg.shortInitialMargin - 1.0)
        // A short so large that slippage alone breaches the maintenance margin would be force-covered at once.
        val liability = qty.valueAt(price).toDollarsDouble()
        if ((amount + margin).toDollarsDouble() < cfg.shortMaintenanceMargin * liability * 1.05) {
            return err(GameError.Invalid("Too large for this market: slippage would trigger an immediate forced cover. Try a smaller amount."))
        }
        return Result.Ok(state, OrderPreview(qty, amount, fee, slip, fill, net = margin + fee))
    }

    fun openShort(state: GameState, id: String, amount: Money, events: MutableList<GameEvent>): Result {
        val p = previewShort(state, id, amount)
        if (p !is Result.Ok) return p
        val pv = p.preview
        val margin = pv.net - pv.fee
        val collateral = amount + margin
        val posted = Ledger.post(
            state.ledger, state.simTimeMs, TxnCategory.Investment, "short ${engine.tickerOf(id)}",
            listOf(
                Posting(Accounts.CASH, -(margin + pv.fee)),
                Posting(MarketEngine.COLLATERAL, collateral),
                Posting(MarketEngine.SHORT_LIABILITY, -amount),
                Posting(Accounts.expense("market_fees"), pv.fee),
            ),
        )
        val ledger = when (posted) {
            is Ledger.Result.Ok -> posted.state
            is Ledger.Result.Err -> return err(insufficient(posted.error))
        }
        val sp = ShortPosition(pv.quantity, amount, collateral, state.market.tick)
        events += GameEvent.TradeExecuted(id, buy = false, value = amount)
        return Result.Ok(state.copy(ledger = ledger, market = state.market.copy(shorts = state.market.shorts + (id to sp), trades = state.market.trades + 1)), pv)
    }

    fun previewCover(state: GameState, id: String): Result {
        val sp = state.market.shorts[id] ?: return err(GameError.NotFound("No short in $id"))
        val price = math.priceMicros(state, id) ?: return err(GameError.Locked("No quote"))
        val liability = sp.qty.valueAt(price)
        val slip = math.slippage(state, id, liability.toDollarsDouble())
        val fill = math.toMicros(price / 1e6 * (1 + slip))
        val cost = sp.qty.valueAt(fill)
        val fee = cost * Multiplier.ofDouble(cfg.feeRate)
        val gain = sp.entryValue - cost
        val taxable = gain - fee
        val tax = if (taxable.isPositive) taxable * Multiplier.ofDouble(cfg.capitalGainsTax) else Money.ZERO
        val back = (sp.collateral - cost - fee - tax).max(Money.ZERO)
        return Result.Ok(state, OrderPreview(sp.qty, cost, fee, slip, fill, tax, back, gain))
    }

    fun cover(state: GameState, id: String, forced: Boolean, events: MutableList<GameEvent>): Result {
        val p = previewCover(state, id)
        if (p !is Result.Ok) return p
        val pv = p.preview
        val sp = state.market.shorts.getValue(id)
        // Tax only comes out of what is returned; when the cover costs more than the collateral the broker absorbs it.
        val tax = pv.tax.min((sp.collateral - pv.gross - pv.fee).max(Money.ZERO))
        val back = (sp.collateral - pv.gross - pv.fee - tax).max(Money.ZERO)
        val shortfall = (pv.gross + pv.fee + tax - sp.collateral).max(Money.ZERO)
        val posted = Ledger.post(
            state.ledger, state.simTimeMs, TxnCategory.Investment, (if (forced) "forced cover " else "cover ") + engine.tickerOf(id),
            listOf(
                Posting(MarketEngine.COLLATERAL, -sp.collateral),
                Posting(MarketEngine.SHORT_LIABILITY, sp.entryValue),
                Posting(Accounts.CASH, back),
                Posting(Accounts.expense("market_fees"), pv.fee),
                Posting(Accounts.expense("tax"), tax),
                Posting(Accounts.revenue("capital_gains"), -pv.realizedGain),
                Posting(Accounts.WORLD, -shortfall),
            ),
            floors = emptyMap(),
        ) as Ledger.Result.Ok
        events += GameEvent.ShortCovered(id, pv.realizedGain, forced)
        return Result.Ok(
            state.copy(ledger = posted.state, market = state.market.copy(shorts = state.market.shorts - id, trades = state.market.trades + if (forced) 0 else 1)),
            pv.copy(tax = tax, net = back),
        )
    }

    private fun insufficient(e: LedgerError): GameError = when (e) {
        is LedgerError.InsufficientFunds -> GameError.InsufficientFunds(e.needed, e.available)
    }
}
