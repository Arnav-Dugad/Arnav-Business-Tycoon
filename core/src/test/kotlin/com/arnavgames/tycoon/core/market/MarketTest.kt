package com.arnavgames.tycoon.core.market

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withCash
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.PackedIntsSerializer
import com.arnavgames.tycoon.core.state.PriceHistory
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val m = engine.content.markets
    private val math = engine.market.math

    private fun atChapter(s: GameState, ch: Int) = s.copy(progress = s.progress.copy(chapter = ch))
    private fun rich(ch: Int = 5) = atChapter(newGame(3).withHugeCash(), ch)
    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")

    @Test
    fun marketOpensWithTheWorldWithHistory() {
        val s = newGame(1)
        assertEquals(m.instruments.size, s.market.quotes.size)
        s.market.quotes.forEach { (id, q) ->
            assertEquals(m.config.historyFinePoints, q.fine.size, id)
            assertEquals(m.config.historyCoarsePoints, q.coarse.size, id)
            assertTrue(q.price.isFinite() && q.price > 0, id)
        }
        assertTrue(s.market.news.isNotEmpty())
    }

    /** Ten game days: every price stays finite and positive, commodities stay near their anchor band, news flows. */
    @Test
    fun tenDaysOfPricesStaySane() {
        val events = mutableListOf<GameEvent>()
        var s = newGame(11)
        repeat(10) { s = engine.advance(s, day).also { events += it.events }.state }
        s.market.quotes.forEach { (id, q) -> assertTrue(q.price.isFinite() && q.price > 0 && q.logV.isFinite() && q.x.isFinite(), "$id: ${q.price}") }
        m.commodities.forEach { c ->
            val ratio = s.market.quotes.getValue(c.id).price / c.anchorPrice.toDouble()
            assertTrue(ratio in 0.4..2.5, "${c.id} drifted to ×$ratio")
        }
        val news = events.count { it is GameEvent.MarketNews }
        // About 1 story per 20 minutes: 720 expected in 10 days.
        assertTrue(news in 550..900, "news count $news")
        assertEquals(3, events.count { it is GameEvent.EarningsSeason }, "earnings every 3 days")
        val funds = m.funds.map { s.market.quotes.getValue(it.id).price }
        funds.forEach { assertTrue(it in 60.0..250.0, "fund NAV $it") }
    }

    /** Quotes are a pure function of seed and minute: trading never changes them, and splits don't either. */
    @Test
    fun quotesIgnoreThePlayerAndHowTimeIsSplit() {
        val base = rich()
        val traded = base.run(Command.BuyInstrument(m.stocks[0].id, Money.ofDollars(1_000_000_000_000)))
            .run(Command.BuyInstrument("fund_total", Money.ofDollars(5_000_000)))
            .run(Command.OpenShort(m.stocks[5].id, Money.ofDollars(1_000_000)))
        val a = base.adv(5 * hour)
        val b = traded.adv(5 * hour)
        var c = base
        listOf(1L, 59_999L, 60_001L, 7 * 60_000L, hour, 3 * hour + 59_999L).forEach { c = c.adv(it) }
        c = c.adv(5 * hour - listOf(1L, 59_999L, 60_001L, 7 * 60_000L, hour, 3 * hour + 59_999L).sum())
        assertEquals(a.market.quotes, b.market.quotes)
        assertEquals(a.market.quotes, c.market.quotes)
        assertEquals(a.market.news, c.market.news)
    }

    @Test
    fun buyThenSellUpdatesPortfolioAndLedger() {
        val id = m.stocks.first { it.band == "core" }.id
        val s0 = atChapter(newGame(5).withCash(1_000_000), 2)
        val s1 = s0.run(Command.BuyInstrument(id, Money.ofDollars(10_000)))
        trial(s1)
        assertEquals(s0.cash - Money.ofDollars(10_000), s1.cash)
        val h = s1.market.holdings.getValue(id)
        assertTrue(h.qty.isPositive)
        assertEquals(Money.ofDollars(9_990), h.costBasis, "cost basis is the amount minus the 0.1% fee")
        assertEquals(Money.ofDollars(9_990), s1.ledger.balance(MarketEngine.INVESTMENTS))
        assertEquals(1, s1.market.trades)
        // Net worth counts the position at market value (slippage on a $10K order is negligible).
        val nwDrop = engine.economy.netWorth(s0) - engine.economy.netWorth(s1)
        assertTrue(nwDrop < Money.ofDollars(30), "net worth only loses fees and slippage, lost $nwDrop")
        val s2 = s1.run(Command.SellInstrument(id, 500))
        trial(s2)
        assertTrue(s2.market.holdings.getValue(id).qty < h.qty)
        val s3 = s2.run(Command.SellInstrument(id, 1000))
        trial(s3)
        assertTrue(id !in s3.market.holdings)
        assertTrue(s3.ledger.balance(MarketEngine.INVESTMENTS).isZero, "all cost basis released")
        assertEquals(3, s3.market.trades)
        val lost = s0.cash - s3.cash
        assertTrue(lost.isPositive && lost < Money.ofDollars(40), "round trip costs only fees and slippage: $lost")
    }

    @Test
    fun featuresUnlockByChapter() {
        val s1 = newGame(2).withCash(1_000_000)
        assertInstanceOf(GameError.Locked::class.java, s1.reject(Command.BuyInstrument(m.stocks[0].id, Money.ofDollars(100))))
        val s2 = atChapter(s1, 2)
        s2.run(Command.BuyInstrument(m.stocks[0].id, Money.ofDollars(100)))
        assertInstanceOf(GameError.Locked::class.java, s2.reject(Command.BuyInstrument(m.coins[0].id, Money.ofDollars(100))))
        assertInstanceOf(GameError.Locked::class.java, atChapter(s1, 4).reject(Command.OpenShort(m.stocks[0].id, Money.ofDollars(100))))
        atChapter(s1, 3).run(Command.BuyInstrument(m.coins[0].id, Money.ofDollars(100)))
        atChapter(s1, 5).run(Command.OpenShort(m.stocks[0].id, Money.ofDollars(100)))
    }

    @Test
    fun slippageGrowsWithSizeAndIsCapped() {
        val s = rich(2)
        val id = m.stocks.minBy { it.baseCapBillions }.id
        val small = math.slippage(s, id, 1_000.0)
        val mid = math.slippage(s, id, 1e8)
        val huge = math.slippage(s, id, 1e15)
        assertTrue(small < 0.001 && small < mid && mid < huge, "$small $mid $huge")
        assertEquals(m.config.slippageMax, huge)
        // Deeper markets in later chapters mean less slippage for the same order.
        assertTrue(math.slippage(atChapter(s, 8), id, 1e8) < mid)
    }

    @Test
    fun stakeIsCappedAndBoardSeatsGivePerks() {
        val stock = m.stocks.filter { it.sector == "consumer" }.minBy { it.baseCapBillions }
        var s = rich(3).run(Command.OpenBusiness("retail"))
        val rateBefore = engine.economy.businessRate(s, "retail").gross
        // An order for the whole company is trimmed to the ownership limit (like a broker), never above it.
        val whole = s.run(Command.BuyInstrument(stock.id, Money.ofDollars((stock.baseCapBillions * 1e9).toLong())))
        val capped = math.stake(whole, stock.id)
        assertTrue(capped <= m.config.maxStake + 1e-9 && capped > m.config.maxStake - 0.01, "trimmed stake $capped")
        // At the limit, buying more is refused with a clear reason.
        assertInstanceOf(GameError.Invalid::class.java, whole.reject(Command.BuyInstrument(stock.id, Money.ofDollars(1_000_000))))
        // Exact share orders work and respect the same limit.
        val exact = s.run(Command.BuyShares(stock.id, java.math.BigInteger.valueOf(1_000_000L * 10)))
        assertEquals(java.math.BigInteger.valueOf(10_000_000L), exact.market.holdings.getValue(stock.id).qty.micro)
        // Buy a 6% stake (plus slippage headroom).
        s = s.run(Command.BuyInstrument(stock.id, Money.ofDollars((stock.baseCapBillions * 1e9 * 0.075).toLong())))
        assertTrue(math.stake(s, stock.id) >= m.config.boardSeatStake, "stake ${math.stake(s, stock.id)}")
        assertEquals(listOf(stock.id), math.boardSeats(s))
        val rateAfter = engine.economy.businessRate(s, "retail").gross
        assertEquals(rateBefore * com.arnavgames.tycoon.core.money.Multiplier.of("1.05"), rateAfter)
        // A later chapter deepens the market: the same shares become a smaller stake.
        assertTrue(math.stake(atChapter(s, 5), stock.id) < m.config.boardSeatStake)
    }

    @Test
    fun dividendsAndCouponsArriveEverySixHours() {
        val stock = m.stocks.maxBy { it.dividendPerDay }
        var s = rich(2).run(Command.BuyInstrument(stock.id, Money.ofDollars(1_000_000))).run(Command.BuyInstrument("bond_gov_5", Money.ofDollars(1_000_000)))
        s = s.adv(13 * hour)
        val div = -s.ledger.balance(Accounts.revenue("dividends"))
        val cpn = -s.ledger.balance(Accounts.revenue("coupons"))
        assertTrue(div > Money.ofDollars(500) && div < Money.ofDollars(5_000), "dividends $div")
        assertTrue(cpn > Money.ofDollars(1_000) && cpn < Money.ofDollars(10_000), "coupons $cpn")
        trial(s)
    }

    /** A short whose price explodes is force-covered; the loss never exceeds the collateral and cash never goes negative. */
    @Test
    fun shortLossesAreCappedByLiquidation() {
        val id = m.stocks[7].id
        var s = atChapter(newGame(9).withCash(1_000_000), 5).run(Command.OpenShort(id, Money.ofDollars(100_000)))
        val sp = s.market.shorts.getValue(id)
        assertEquals(Money.ofDollars(150_000), sp.collateral)
        trial(s)
        val cashAfterOpen = s.cash
        // The stock gaps up 5× (news, takeover…): the next tick must liquidate.
        val q = s.market.quotes.getValue(id)
        s = s.copy(market = s.market.copy(quotes = s.market.quotes + (id to q.copy(logV = q.logV + kotlin.math.ln(5.0)))))
        val r = engine.advance(s, 60_000)
        s = r.state
        assertTrue(id !in s.market.shorts, "forced cover")
        assertTrue(r.events.any { it is GameEvent.ShortCovered && it.forced })
        assertEquals(cashAfterOpen, s.cash, "the whole collateral was used, nothing more")
        assertTrue(!s.cash.isNegative)
        trial(s)
    }

    @Test
    fun profitableShortReturnsCollateralPlusGain() {
        val id = m.stocks[8].id
        var s = atChapter(newGame(9).withCash(1_000_000), 5)
        val start = s.cash
        s = s.run(Command.OpenShort(id, Money.ofDollars(100_000)))
        val q = s.market.quotes.getValue(id)
        s = s.copy(market = s.market.copy(quotes = s.market.quotes + (id to q.copy(logV = q.logV + kotlin.math.ln(0.8)))))
        s = s.run(Command.CoverShort(id))
        val gain = s.cash - start
        // ~20% of $100K minus fees and 10% tax on the gain.
        assertTrue(gain > Money.ofDollars(16_000) && gain < Money.ofDollars(19_000), "gain $gain")
        trial(s)
    }

    @Test
    fun divisionStakeDiscountsTheMatchingBusiness() {
        val stock = m.stocks.filter { it.sector == "materials" }.minBy { it.baseCapBillions }
        var s = rich(4)
        val full = engine.economy.openCost(s, engine.content.business("mining"))
        s = s.run(Command.BuyInstrument(stock.id, Money.ofDollars((stock.baseCapBillions * 1e9 * 10 * 0.30).toLong())))
        assertTrue(math.stake(s, stock.id) >= m.config.divisionStake)
        assertEquals(full * com.arnavgames.tycoon.core.money.Multiplier.of("0.75"), engine.economy.openCost(s, engine.content.business("mining")))
    }

    @Test
    fun missionsCountTradesAndSeats() {
        var s = atChapter(newGame(4).withCash(10_000_000), 2)
        assertEquals(0.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.MarketTrades(1)))
        s = s.run(Command.BuyInstrument("fund_total", Money.ofDollars(1_000)))
        assertEquals(1.0, engine.goalProgress(s, com.arnavgames.tycoon.core.content.GoalDef.MarketTrades(1)))
    }

    @Test
    fun packedHistoryRoundTripsExactly(): Unit = runBlocking {
        val ser = ListSerializer(PackedIntsSerializer)
        checkAll(200, Arb.list(Arb.list(Arb.int(-400_000..400_000), 0..120), 1..4)) { lists ->
            val json = Json.encodeToString(ser, lists)
            assertEquals(lists, Json.decodeFromString(ser, json))
        }
        assertEquals(12345, PriceHistory.key(PriceHistory.price(12345)))
    }

    @Test
    fun netWorthIncludesPortfolioAndShortEquity() {
        var s = rich(5)
        val before = engine.economy.netWorth(s)
        s = s.run(Command.BuyInstrument("fund_total", Money.ofDollars(1_000_000))).run(Command.OpenShort(m.stocks[3].id, Money.ofDollars(1_000_000)))
        val after = engine.economy.netWorth(s)
        val diff = before - after
        // Only fees and slippage are lost; collateral minus liability counts as equity.
        assertTrue(diff.isPositive && diff < Money.ofDollars(5_000), "net worth changed by $diff")
        assertNotEquals(Money.ZERO, math.shortEquity(s))
    }
}
