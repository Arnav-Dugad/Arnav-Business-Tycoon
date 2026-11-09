package com.arnavgames.tycoon.ui.markets

import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.market.MarketEngine
import com.arnavgames.tycoon.core.market.Trading
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.PriceHistory
import java.util.Locale
import kotlin.math.abs

enum class MarketTab(val label: String, val feature: String) {
    Stocks("Stocks", "stocks"), Funds("Funds & bonds", "funds"), Crypto("Crypto", "crypto"), Commodities("Commodities", "commodities")
}

data class InstrumentRowUi(
    val id: String,
    val ticker: String,
    val name: String,
    val subtitle: String,
    val assetClass: AssetClass,
    val group: String,
    val price: String,
    val change: Double,
    val spark: List<Float>,
    val held: Boolean,
    /** For sorting: the raw price and what it pays a day (dividend or coupon; 0 for none). */
    val priceRaw: Double = 0.0,
    val yieldPerDay: Double = 0.0,
)

data class PositionUi(val id: String, val ticker: String, val name: String, val value: Money, val pnl: Money, val pnlPct: Double, val short: Boolean, val assetClass: AssetClass)

data class NewsUi(val id: Long, val headline: String, val reliability: String, val effect: Double, val minutesAgo: Long, val target: String, val targetTicker: String?)

data class MarketsUi(
    val unlocked: Boolean,
    val unlockChapter: Int,
    val portfolioValue: Money,
    val dayChange: Double,
    val cash: Money,
    val phase: String,
    val ratePerDay: Double,
    val tabsUnlocked: Map<MarketTab, Int?>,
    val rows: Map<MarketTab, List<InstrumentRowUi>>,
    val sectors: List<Pair<String, String>>,
    val positions: List<PositionUi>,
    val news: List<NewsUi>,
    val boardSeats: Int,
)

data class StatUi(val label: String, val value: String)

data class InstrumentUi(
    val id: String,
    val ticker: String,
    val name: String,
    val assetClass: AssetClass,
    val kicker: String,
    val price: String,
    val priceRaw: Double,
    val change: Double,
    val day: List<Float>,
    val week: List<Float>,
    val about: String,
    val stats: List<StatUi>,
    val holdingValue: Money?,
    val holdingQty: String?,
    val costBasis: Money?,
    val pnl: Money?,
    val stake: Double?,
    val boardSeatStake: Double,
    val divisionStake: Double,
    val short: ShortUi?,
    val canShort: Boolean,
    val shortLockedReason: String?,
    val cash: Money,
    val locked: String?,
    val news: List<NewsUi>,
    /** Roughly what a board-seat stake costs at today's price (stocks only). */
    val seatCost: Money? = null,
    /** A stock's sector painting (the sector's first business), shown at the top of its page. */
    val art: String? = null,
)

data class ShortUi(val collateral: Money, val liability: Money, val pnl: Money, val health: Double)

enum class OrderSide { Buy, Sell, Short }

data class OrderPreviewUi(
    val ok: Boolean,
    val error: String?,
    val lines: List<Pair<String, String>>,
    val actionLabel: String,
)

/** Maps engine state to the Markets UI. Instrument rows are cached per market tick (prices change once a minute). */
class MarketsMapper(private val engine: GameEngine) {
    private val m = engine.content.markets
    private val math = engine.market.math
    private var cachedTick = Long.MIN_VALUE
    private var cachedHeld: Set<String> = emptySet()
    private var cachedRows: Map<MarketTab, List<InstrumentRowUi>> = emptyMap()

    fun toUi(s: GameState): MarketsUi {
        val held = s.market.holdings.keys + s.market.shorts.keys
        if (s.market.tick != cachedTick || held != cachedHeld) {
            cachedRows = buildRows(s, held)
            cachedTick = s.market.tick
            cachedHeld = held
        }
        val portfolio = math.portfolioValue(s) + math.shortEquity(s)
        // Day change of the portfolio: value-weighted 24 h change of each position.
        var weighted = 0.0
        var total = 0.0
        for (id in s.market.holdings.keys) {
            val v = math.holdingValue(s, id).toDollarsDouble()
            weighted += v * math.change24h(s, id)
            total += v
        }
        val positions = s.market.holdings.keys.sorted().map { id ->
            val h = s.market.holdings.getValue(id)
            val value = math.holdingValue(s, id)
            val ref = m.instrumentById.getValue(id)
            PositionUi(id, ref.ticker, ref.name, value, value - h.costBasis, ratio(value - h.costBasis, h.costBasis), short = false, ref.assetClass)
        } + s.market.shorts.keys.sorted().map { id ->
            val sp = s.market.shorts.getValue(id)
            val liability = math.shortLiability(s, id)
            val ref = m.instrumentById.getValue(id)
            PositionUi(id, ref.ticker, ref.name, (sp.collateral - liability).max(Money.ZERO), sp.entryValue - liability, ratio(sp.entryValue - liability, sp.entryValue), short = true, ref.assetClass)
        }
        return MarketsUi(
            unlocked = math.isUnlocked(s, "stocks"),
            unlockChapter = math.unlockChapter("stocks"),
            portfolioValue = portfolio,
            dayChange = if (total > 0) weighted / total else 0.0,
            cash = s.cash,
            phase = engine.market.phaseName(s),
            ratePerDay = engine.market.currentRate(s),
            tabsUnlocked = MarketTab.entries.associateWith { t -> if (math.isUnlocked(s, t.feature)) null else math.unlockChapter(t.feature) },
            rows = cachedRows,
            sectors = m.sectors.map { it.id to it.name },
            positions = positions,
            news = news(s, null).take(30),
            boardSeats = math.boardSeats(s).size,
        )
    }

    private fun buildRows(s: GameState, held: Set<String>): Map<MarketTab, List<InstrumentRowUi>> {
        fun row(id: String, subtitle: String, group: String, yieldPerDay: Double = 0.0): InstrumentRowUi {
            val ref = m.instrumentById.getValue(id)
            val q = s.market.quotes.getValue(id)
            val spark = (q.fine.map { PriceHistory.price(it).toFloat() } + q.price.toFloat())
            return InstrumentRowUi(id, ref.ticker, ref.name, subtitle, ref.assetClass, group, fmtPrice(q.price), math.change24h(s, id), spark, id in held, q.price, yieldPerDay)
        }
        return mapOf(
            MarketTab.Stocks to m.stocks.map { st -> row(st.id, "${st.ticker} · ${bandWord(st.band)}", st.sector, st.dividendPerDay) },
            MarketTab.Funds to m.funds.map { f -> row(f.id, "${f.ticker} · Fund", "funds") } +
                m.bonds.map { b -> engine.market.couponPerDay(s, b.id).let { cp -> row(b.id, "${b.ticker} · pays ${MarketEngine.pct(cp, 2)}/day", "bonds", cp) } },
            MarketTab.Crypto to m.coins.map { c -> row(c.id, "${c.ticker} · ${tierWord(c.tier)}", "tier${c.tier}") },
            MarketTab.Commodities to m.commodities.map { c -> row(c.id, "per ${c.unit}", "commodities") },
        )
    }

    fun news(s: GameState, target: String?): List<NewsUi> = s.market.news.asReversed()
        .filter { target == null || it.target == target }
        .map { n ->
            NewsUi(n.id, n.headline, n.reliability, n.effect, (s.market.tick - n.tick).coerceAtLeast(0), n.target, m.instrumentById[n.target]?.ticker)
        }

    fun detail(s: GameState, id: String): InstrumentUi? {
        val ref = m.instrumentById[id] ?: return null
        val q = s.market.quotes[id] ?: return null
        val price = q.price
        val day = q.fine.map { PriceHistory.price(it).toFloat() } + price.toFloat()
        val week = q.coarse.map { PriceHistory.price(it).toFloat() } + q.fine.filterIndexed { i, _ -> i % 12 == 0 }.map { PriceHistory.price(it).toFloat() } + price.toFloat()
        val scale = math.depthScale(s.progress.chapter)
        val stats = mutableListOf<StatUi>()
        var about = ""
        var kicker = ""
        var cap: Money? = null
        var art: String? = null
        when (ref.assetClass) {
            AssetClass.Stock -> {
                val st = m.stockById.getValue(id)
                val sector = m.sectorById.getValue(st.sector)
                kicker = "${sector.name} · ${bandWord(st.band)}"
                art = sector.businesses.firstOrNull()?.let { com.arnavgames.tycoon.ui.common.Art.business(it) }
                about = "${st.trait}. ${bandAbout(st.band)}"
                cap = Money.ofDollars(java.math.BigDecimal(st.baseCapBillions * 1e9 * scale * price / st.startPrice.toDouble()))
                stats += StatUi("Market value", com.arnavgames.tycoon.core.money.MoneyFormat.compact(cap))
                stats += StatUi("Dividend", if (st.dividendPerDay > 0) "${MarketEngine.pct(st.dividendPerDay, 2)} a day" else "None")
                val swing = when { st.beta < 0.8 -> "Calm"; st.beta <= 1.2 -> "Average"; else -> "Wild" }
                stats += StatUi("Market swings", "$swing (β ${String.format(Locale.US, "%.2f", st.beta)})")
                stats += StatUi("Board seat perk", "+5% income at " + sector.businesses.joinToString { engine.content.business(it).name })
            }
            AssetClass.Fund -> {
                val f = m.fundById.getValue(id)
                kicker = "Index fund"
                about = f.description
                stats += StatUi("Dividend", "${MarketEngine.pct(engine.market.fundDividendPerDay.getValue(id), 2)} a day")
                stats += StatUi("Fee", "${MarketEngine.pct(f.feePerDay, 3)} a day")
            }
            AssetClass.Bond -> {
                val b = m.bondById.getValue(id)
                kicker = when (b.kind) { "government" -> "Government bond"; "investment_grade" -> "Investment-grade bond"; else -> "High-yield bond" }
                about = "Pays a coupon every 6 hours. Prices fall when the central bank raises rates, more so for longer bonds." +
                    if (b.defaultChance > 0) " High-yield issuers can default in a recession." else ""
                stats += StatUi("Coupon", "${MarketEngine.pct(engine.market.couponPerDay(s, id), 2)} a day")
                stats += StatUi("Duration", String.format(Locale.US, "%.1f", b.duration))
                stats += StatUi("Default risk", if (b.defaultChance > 0) "In recessions" else "None")
            }
            AssetClass.Crypto -> {
                val c = m.coinById.getValue(id)
                kicker = "Crypto · ${tierWord(c.tier)}"
                about = c.risk
                stats += StatUi("Staking", if (c.stakingPerDay > 0) "${MarketEngine.pct(c.stakingPerDay, 2)} a day" else "None")
                stats += StatUi("Daily swings", "about ${MarketEngine.pct(c.volPerDay, 0)}")
                stats += StatUi("Crash risk", if (c.crashChancePerDay > 0) "Real" else "Low")
            }
            AssetClass.Commodity -> {
                val c = m.commodityById.getValue(id)
                kicker = "Commodity · per ${c.unit}"
                about = "Prices drift back toward a cost-of-production level, but shocks from news can push them far away for a while."
                stats += StatUi("Long-run price", "\$" + c.anchorPrice)
                val rev = c.effects.filter { it.mode == "revenue" }.map { engine.content.business(it.business).name }
                val upk = c.effects.filter { it.mode == "upkeep" }.map { engine.content.business(it.business).name }
                if (rev.isNotEmpty()) stats += StatUi("Raises revenue of", rev.joinToString())
                if (upk.isNotEmpty()) stats += StatUi("Raises costs of", upk.joinToString())
            }
        }
        val h = s.market.holdings[id]
        val value = h?.let { math.holdingValue(s, id) }
        val sp = s.market.shorts[id]
        val shortUi = sp?.let {
            val liability = math.shortLiability(s, id)
            val maint = m.config.shortMaintenanceMargin
            val ratio = if (liability.isPositive) it.collateral.toDollarsDouble() / liability.toDollarsDouble() else 9.9
            ShortUi(it.collateral, liability, it.entryValue - liability, ((ratio - maint) / (m.config.shortInitialMargin - maint)).coerceIn(0.0, 1.0))
        }
        val feature = math.featureOf(ref.assetClass)
        val shortLocked = when {
            ref.assetClass != AssetClass.Stock -> "Only stocks can be sold short"
            !math.isUnlocked(s, "shorts") -> "Short selling unlocks in chapter ${math.unlockChapter("shorts")}"
            h != null -> "Sell your shares before shorting"
            else -> null
        }
        return InstrumentUi(
            id = id, ticker = ref.ticker, name = ref.name, assetClass = ref.assetClass, kicker = kicker, art = art,
            price = fmtPrice(price), priceRaw = price, change = math.change24h(s, id), day = day, week = week, about = about, stats = stats,
            holdingValue = value, holdingQty = h?.let { fmtQty(it.qty.toDouble()) }, costBasis = h?.costBasis, pnl = value?.let { it - h.costBasis },
            stake = if (ref.assetClass == AssetClass.Stock) math.stake(s, id) else null,
            boardSeatStake = m.config.boardSeatStake, divisionStake = m.config.divisionStake,
            short = shortUi, canShort = shortLocked == null, shortLockedReason = shortLocked,
            cash = s.cash,
            locked = if (math.isUnlocked(s, feature)) null else "Unlocks in chapter ${math.unlockChapter(feature)}",
            news = news(s, id).take(5),
            seatCost = cap?.let { it * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(m.config.boardSeatStake) },
        )
    }

    /** Preview of an order: [fraction] of cash (buy/short) or of the position (sell). */
    fun preview(s: GameState, id: String, side: OrderSide, fraction: Double, fmt: (Money) -> String): OrderPreviewUi {
        val t = engine.trading
        val amount = s.cash * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(fraction)
        val r = when (side) {
            OrderSide.Buy -> t.previewBuy(s, id, amount)
            OrderSide.Sell -> t.previewSell(s, id, (fraction * 1000).toInt().coerceIn(1, 1000))
            OrderSide.Short -> if (id in s.market.shorts) t.previewCover(s, id) else t.previewShort(s, id, amount * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(SHORT_NOTIONAL))
        }
        return when (r) {
            is Trading.Result.Err -> OrderPreviewUi(false, com.arnavgames.tycoon.ui.GameViewModel.errorText(r.error), emptyList(), side.name)
            is Trading.Result.Ok -> withSlippageWarning(r.preview.slippage, previewOk(r, id, side, s, fmt))
        }
    }

    /** What the ticket would do, and the command that does it. */
    data class Ticket(val ui: OrderPreviewUi, val command: com.arnavgames.tycoon.core.engine.Command?, val maxNote: String? = null)

    /**
     * The exact-amount ticket: [value] is dollars or [byShares] units. Buying above the 49% ownership limit is trimmed
     * to it (and says so); selling more than you hold sells it all.
     */
    fun ticket(s: GameState, id: String, side: OrderSide, byShares: Boolean, value: java.math.BigDecimal?, fmt: (Money) -> String): Ticket {
        val t = engine.trading
        val math = engine.market.math
        val price = math.priceMicros(s, id) ?: return Ticket(OrderPreviewUi(false, "The market has not opened yet", emptyList(), side.name), null)
        val unit = unitWord(m.instrumentById.getValue(id).assetClass)
        val room = t.roomToBuy(s, id)
        val maxNote = when (side) {
            OrderSide.Buy -> room?.let { r -> if (r.isPositive) "You can own up to ${(m.config.maxStake * 100).toInt()}%: ${fmtQty(r.toDouble())} more $unit (about ${fmt(r.valueAt(price))})" else "You own the most allowed (${(m.config.maxStake * 100).toInt()}%)" }
            OrderSide.Sell -> s.market.holdings[id]?.let { "You hold ${fmtQty(it.qty.toDouble())} $unit" }
            OrderSide.Short -> null
        }
        if (value == null || value.signum() <= 0) return Ticket(OrderPreviewUi(false, null, emptyList(), if (side == OrderSide.Sell) "Sell" else "Buy"), null, maxNote)
        fun qtyOf(v: java.math.BigDecimal) = com.arnavgames.tycoon.core.state.Qty(v.movePointRight(6).setScale(0, java.math.RoundingMode.DOWN).toBigInteger())
        // Dollars → units at the current price (selling), for an exact quantity.
        fun qtyForDollars(v: java.math.BigDecimal) = qtyOf(v.divide(java.math.BigDecimal(price).movePointLeft(6), 6, java.math.RoundingMode.DOWN))
        val (r, cmd) = when (side) {
            OrderSide.Buy -> if (byShares) {
                val q = qtyOf(value)
                t.previewBuyShares(s, id, q) to com.arnavgames.tycoon.core.engine.Command.BuyShares(id, q.micro)
            } else {
                val amount = Money.ofDollars(value)
                t.previewBuy(s, id, amount) to com.arnavgames.tycoon.core.engine.Command.BuyInstrument(id, amount)
            }
            OrderSide.Sell -> {
                val raw = if (byShares) qtyOf(value) else qtyForDollars(value)
                // Within 0.1% of the whole position (a price tick since you tapped Max): sell it all, never leave dust.
                val held = s.market.holdings[id]?.qty
                val q = if (held != null && raw.micro.multiply(java.math.BigInteger.valueOf(1000)) >= held.micro.multiply(java.math.BigInteger.valueOf(999))) held else raw
                t.previewSellQty(s, id, q) to com.arnavgames.tycoon.core.engine.Command.SellShares(id, q.micro)
            }
            OrderSide.Short -> return Ticket(preview(s, id, side, 0.25, fmt), null)
        }
        return when (r) {
            is Trading.Result.Err -> Ticket(OrderPreviewUi(false, com.arnavgames.tycoon.ui.GameViewModel.errorText(r.error), emptyList(), side.name), null, maxNote)
            is Trading.Result.Ok -> {
                val p = r.preview
                if (side == OrderSide.Buy && p.gross > s.cash) {
                    Ticket(OrderPreviewUi(false, "That costs ${fmt(p.gross)}; you have ${fmt(s.cash)}.", emptyList(), "Buy"), null, maxNote)
                } else {
                    var ui = withSlippageWarning(p.slippage, previewOk(r, id, side, s, fmt))
                    if (p.trimmed) ui = ui.copy(lines = listOf("Trimmed" to "to the ${(m.config.maxStake * 100).toInt()}% ownership limit") + ui.lines)
                    // Buying a dollar amount: the order that runs is exactly the quantity shown (it never re-trims).
                    val finalCmd = if (side == OrderSide.Buy) com.arnavgames.tycoon.core.engine.Command.BuyShares(id, p.quantity.micro) else cmd
                    Ticket(ui, finalCmd, maxNote)
                }
            }
        }
    }

    /** The largest sensible value for the Max chip: all your cash (or room under the limit), or your whole position. */
    fun maxValue(s: GameState, id: String, side: OrderSide, byShares: Boolean): java.math.BigDecimal {
        val math = engine.market.math
        val price = math.priceMicros(s, id) ?: return java.math.BigDecimal.ZERO
        val p = java.math.BigDecimal(price).movePointLeft(6)
        return when (side) {
            OrderSide.Sell -> {
                val q = s.market.holdings[id]?.qty ?: return java.math.BigDecimal.ZERO
                val units = java.math.BigDecimal(q.micro).movePointLeft(6)
                if (byShares) units else units.multiply(p).setScale(2, java.math.RoundingMode.DOWN)
            }
            else -> {
                // Cash, less a little for fees and slippage; capped by the ownership limit.
                val cash = java.math.BigDecimal(s.cash.cents).movePointLeft(2).multiply(java.math.BigDecimal("0.97"))
                val room = engine.trading.roomToBuy(s, id)?.let { java.math.BigDecimal(it.micro).movePointLeft(6) }
                val unitsByCash = cash.divide(p, 6, java.math.RoundingMode.DOWN)
                val units = room?.min(unitsByCash) ?: unitsByCash
                if (byShares) units else units.multiply(p).setScale(2, java.math.RoundingMode.DOWN)
            }
        }
    }

    private fun withSlippageWarning(slippage: Double, ui: OrderPreviewUi): OrderPreviewUi =
        if (slippage <= 0.02) ui else ui.copy(lines = ui.lines + ("Warning" to "large order: ${String.format(Locale.US, "%.1f%%", slippage * 100)} lost to slippage. Smaller orders fill better."))

    private fun previewOk(r: Trading.Result.Ok, id: String, side: OrderSide, s: GameState, fmt: (Money) -> String): OrderPreviewUi {
        return run {
                val p = r.preview
                val unit = unitWord(m.instrumentById.getValue(id).assetClass)
                val fill = fmtPrice(p.fillPriceMicros / 1e6)
                val slip = String.format(Locale.US, "%.2f%%", p.slippage * 100)
                when (side) {
                    OrderSide.Buy -> OrderPreviewUi(true, null, listOf(
                        "You get" to "${fmtQty(p.quantity.toDouble())} $unit",
                        "Average fill" to "$fill (slippage $slip)",
                        "Fee" to fmt(p.fee),
                    ), "Buy · ${fmt(p.gross)}")
                    OrderSide.Sell -> OrderPreviewUi(true, null, listOf(
                        "You sell" to "${fmtQty(p.quantity.toDouble())} $unit",
                        "Average fill" to "$fill (slippage $slip)",
                        "Fee" to fmt(p.fee),
                        "Tax on gains" to fmt(p.tax),
                        "Gain or loss" to signed(p.realizedGain, fmt),
                    ), "Sell · ${fmt(p.net)}")
                    OrderSide.Short -> if (id in s.market.shorts) OrderPreviewUi(true, null, listOf(
                        "Buy back" to "${fmtQty(p.quantity.toDouble())} $unit at $fill",
                        "Gain or loss" to signed(p.realizedGain, fmt),
                        "Tax on gains" to fmt(p.tax),
                    ), "Cover · get ${fmt(p.net)}") else OrderPreviewUi(true, null, listOf(
                        "You borrow and sell" to "${fmtQty(p.quantity.toDouble())} $unit at $fill",
                        "Your margin" to fmt(p.net - p.fee),
                        "Borrow fee" to "${MarketEngine.pct(m.config.borrowFeePerDay, 1)} a day",
                        "Forced cover" to "if collateral drops below ${(m.config.shortMaintenanceMargin * 100).toInt()}% of the position",
                    ), "Short · ${fmt(p.gross)}")
                }
            }
    }

    companion object {
        fun fmtPrice(p: Double): String = when {
            p >= 100_000 -> "$" + String.format(Locale.US, "%,.0f", p)
            p >= 1 -> "$" + String.format(Locale.US, "%,.2f", p)
            p >= 0.01 -> "$" + String.format(Locale.US, "%.4f", p)
            else -> "$" + String.format(Locale.US, "%.6f", p)
        }

        fun fmtQty(q: Double): String = when {
            q >= 1e15 -> String.format(Locale.US, "%.3e", q)
            q >= 1000 -> String.format(Locale.US, "%,.0f", q)
            q >= 1 -> String.format(Locale.US, "%,.2f", q)
            else -> String.format(Locale.US, "%.6f", q)
        }

        fun signed(m: Money, fmt: (Money) -> String) = (if (m.isNegative) "−" else "+") + fmt(m.abs())

        fun ratio(a: Money, b: Money): Double = if (b.isPositive) a.toDollarsDouble() / b.toDollarsDouble() else 0.0

        fun bandWord(b: String) = when (b) { "defensive" -> "Defensive"; "core" -> "Core"; "growth" -> "Growth"; else -> "Speculative" }

        fun bandAbout(b: String) = when (b) {
            "defensive" -> "Steady business, small swings, reliable dividends."
            "core" -> "A solid company that moves with the economy."
            "growth" -> "Grows fast and swings hard. Little or no dividend."
            else -> "A long shot. It can double or halve in days."
        }

        fun tierWord(t: Int) = when (t) { 1 -> "Established"; 2 -> "Large"; 3 -> "Small"; else -> "Speculative" }

        fun unitWord(ac: AssetClass) = when (ac) {
            AssetClass.Stock -> "shares"
            AssetClass.Fund, AssetClass.Bond -> "units"
            AssetClass.Crypto -> "coins"
            AssetClass.Commodity -> "lots"
        }

        fun ago(min: Long): String = when {
            min < 1 -> "now"
            min < 60 -> "${min}m"
            min < 1440 -> "${min / 60}h"
            else -> "${min / 1440}d"
        }

        fun pct(x: Double): String = (if (x >= 0) "+" else "−") + String.format(Locale.US, "%.1f%%", abs(x) * 100)
    }
}
