package com.arnavgames.tycoon.core.market

import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.core.content.MarketContent
import com.arnavgames.tycoon.core.content.NewsTemplateDef
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerState
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.random.Pcg32
import com.arnavgames.tycoon.core.state.EarningsHint
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MacroState
import com.arnavgames.tycoon.core.state.MarketState
import com.arnavgames.tycoon.core.state.NewsItem
import com.arnavgames.tycoon.core.state.PriceHistory
import com.arnavgames.tycoon.core.state.Quote
import com.arnavgames.tycoon.core.state.SectorDrift
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The market's minute tick (docs/ECONOMY.md §5). One tick per game minute. Prices, news, earnings and the macro
 * cycle are drawn from seeded streams keyed by the minute index, so they never depend on the player's actions or on
 * how time was split. Payouts and margin calls do read the player's positions and post through the Ledger.
 */
class MarketEngine(private val m: MarketContent, private val dividendBonus: (GameState) -> Double = { 0.0 }) {
    val math = MarketMath(m)
    private val cfg = m.config
    private val dt = 1.0 / MINUTES_PER_DAY
    private val sqrtDt = sqrt(dt)

    // Fund membership weights (stock index -> weight), precomputed once.
    private val fundWeights: Map<String, List<Pair<Int, Double>>> = m.funds.associate { f ->
        val members = m.stocks.withIndex().filter { (_, s) ->
            when (f.style) {
                "growth" -> s.band == "growth" || s.band == "speculative"
                "defensive" -> s.band == "defensive"
                else -> true
            }
        }
        val weights = if (f.style == "global") {
            val perSector = members.groupBy { it.value.sector }
            members.map { (i, s) -> i to 1.0 / perSector.size / perSector.getValue(s.sector).size }
        } else {
            val total = members.sumOf { it.value.baseCapBillions }
            members.map { (i, s) -> i to s.baseCapBillions / total }
        }
        f.id to weights
    }

    /** Dividend yield per day of each fund (weighted dividends of its members, minus its fee). */
    val fundDividendPerDay: Map<String, Double> = m.funds.associate { f ->
        f.id to (fundWeights.getValue(f.id).sumOf { (i, w) -> w * m.stocks[i].dividendPerDay } - f.feePerDay).coerceAtLeast(0.0)
    }

    private val refRate = if (m.macro.isEmpty()) 0.0 else m.macro.map { it.ratePerDay }.average()

    fun currentRate(state: GameState): Double = m.macro.firstOrNull { it.id == state.market.macro.phase }?.ratePerDay ?: refRate

    fun phaseName(state: GameState): String = m.macro.firstOrNull { it.id == state.market.macro.phase }?.name ?: "Calm"

    fun couponPerDay(state: GameState, bondId: String): Double = currentRate(state) + m.bondById.getValue(bondId).couponSpread

    // =====================================================================================
    // Tick
    // =====================================================================================

    /** Runs the market tick for minute index [minute]. Call once per game minute, in order. */
    fun tick(state: GameState, minute: Long, events: MutableList<GameEvent>): GameState {
        if (m.stocks.isEmpty()) return state
        if (state.market.tick < 0) return initialize(state, minute)
        if (minute <= state.market.tick) return state
        var s = state
        // Normally exactly one minute passes; loop for robustness if a tick was skipped.
        for (t in (state.market.tick + 1)..minute) s = step(s, t, events)
        return s
    }

    private fun step(state: GameState, t: Long, events: MutableList<GameEvent>): GameState {
        var mk = state.market
        mk = macroStep(state.worldSeed, mk, t)
        mk = mk.copy(quotes = priceStep(state.worldSeed, mk, t), tick = t)
        mk = newsStep(state.worldSeed, mk, t, events)
        if (t > 0 && t % (cfg.earningsEveryDays * MINUTES_PER_DAY) == 0L) mk = earnings(state, mk, t, events)
        if (t % cfg.historyFineMinutes == 0L || t % cfg.historyCoarseMinutes == 0L) mk = mk.copy(quotes = recordHistory(mk, t))
        var s = state.copy(market = mk.copy(sectorDrift = mk.sectorDrift.filterValues { it.untilTick > t }))
        if (t % (cfg.payoutEveryHours * 60L) == 0L) s = payouts(s, t, events)
        s = marginCalls(s, t, events)
        s = dilution(s, t)
        return s
    }

    private fun rng(seed: Long, stream: String, key: Long): Pcg32 = GameRandom.stream(seed, stream, key)

    private fun macroStep(seed: Long, mk: MarketState, t: Long): MarketState {
        if (m.macro.isEmpty() || t < mk.macro.phaseEndsTick) return mk
        val r = rng(seed, "macro", t)
        val cur = m.macro.firstOrNull { it.id == mk.macro.phase } ?: m.macro.first()
        val nextId = r.pickWeighted(cur.next.entries.toList()) { it.value }.key
        val next = m.macro.first { it.id == nextId }
        val ends = t + (r.nextDouble(next.minDays, next.maxDays) * MINUTES_PER_DAY).toLong()
        val item = NewsItem(mk.nextNewsId, t, "The economy enters a ${next.name.lowercase()}. The central bank sets its rate at ${pct(next.ratePerDay, 2)} a day.", "macro")
        return mk.copy(macro = MacroState(next.id, ends), news = addNews(mk.news, item), nextNewsId = mk.nextNewsId + 1)
    }

    private fun macroDrift(mk: MarketState): Double = m.macro.firstOrNull { it.id == mk.macro.phase }?.driftPerDay ?: 0.0

    private fun priceStep(seed: Long, mk: MarketState, t: Long): Map<String, Quote> {
        val r = rng(seed, "market", t)
        val out = LinkedHashMap<String, Quote>(mk.quotes.size * 2)
        val macro = macroDrift(mk)
        val theta = cfg.noiseReversionPerDay
        val vShare = sqrt(cfg.fundamentalVolShare)
        val xShare = sqrt(1.0 - cfg.fundamentalVolShare)
        val stockDelta = DoubleArray(m.stocks.size)
        m.stocks.forEachIndexed { i, s ->
            val q = mk.quotes.getValue(s.id)
            val sigmaV = s.volPerDay * vShare
            val sigmaX = s.volPerDay * xShare * sqrt(2 * theta) // keeps the noise's daily contribution comparable
            val sector = mk.sectorDrift[s.sector]?.perDay ?: 0.0
            val mu = ln(1 + s.driftPerDay) - 0.5 * sigmaV * sigmaV + s.beta * macro + sector
            val nv = q.logV + mu * dt + sigmaV * sqrtDt * r.nextGaussian()
            val nx = q.x - theta * q.x * dt + sigmaX * sqrtDt * r.nextGaussian()
            stockDelta[i] = (nv + nx) - (q.logV + q.x)
            out[s.id] = q.copy(logV = nv, x = nx)
        }
        m.funds.forEach { f ->
            val q = mk.quotes.getValue(f.id)
            val d = fundWeights.getValue(f.id).sumOf { (i, w) -> w * stockDelta[i] } - f.feePerDay * dt
            out[f.id] = q.copy(logV = q.logV + d)
        }
        val rate = m.macro.firstOrNull { it.id == mk.macro.phase }?.ratePerDay ?: refRate
        m.bonds.forEach { b ->
            val q = mk.quotes.getValue(b.id)
            val target = ln(100.0) - b.duration * BOND_RATE_SENSITIVITY * (rate - refRate)
            // Credit stress (after a default) decays back to zero; a little noise keeps the chart alive.
            val nx = q.x - 1.0 * q.x * dt + 0.002 * sqrtDt * r.nextGaussian()
            out[b.id] = q.copy(logV = target, x = nx)
        }
        m.coins.forEach { c ->
            val q = mk.quotes.getValue(c.id)
            val hype = if (q.hypeUntil > t) q.hypeDrift else 0.0
            val sigmaV = c.volPerDay * vShare
            val sigmaX = c.volPerDay * xShare * sqrt(2 * theta)
            var nv = q.logV + (ln(1 + c.driftPerDay) - 0.5 * sigmaV * sigmaV + hype + 0.6 * macro) * dt + sigmaV * sqrtDt * r.nextGaussian()
            val nx = q.x - theta * q.x * dt + sigmaX * sqrtDt * r.nextGaussian()
            if (c.crashChancePerDay > 0 && r.nextDouble() < c.crashChancePerDay * dt) nv += ln(1 - r.nextDouble(c.crashDepthMin, c.crashDepthMax))
            out[c.id] = q.copy(logV = nv, x = nx, hypeDrift = if (q.hypeUntil > t) q.hypeDrift else 0.0)
        }
        m.commodities.forEach { c ->
            val q = mk.quotes.getValue(c.id)
            val sigma = c.volPerDay * sqrt(2 * c.reversionPerDay)
            val nx = q.x - c.reversionPerDay * q.x * dt + sigma * sqrtDt * r.nextGaussian()
            out[c.id] = q.copy(x = nx)
        }
        return out
    }

    private fun recordHistory(mk: MarketState, t: Long): Map<String, Quote> {
        val fine = t % cfg.historyFineMinutes == 0L
        val coarse = t % cfg.historyCoarseMinutes == 0L
        val out = LinkedHashMap<String, Quote>(mk.quotes.size * 2)
        for ((id, q) in mk.quotes) {
            val p = PriceHistory.key(q.price)
            out[id] = q.copy(
                fine = if (fine) (q.fine + p).takeLast(cfg.historyFinePoints) else q.fine,
                coarse = if (coarse) (q.coarse + p).takeLast(cfg.historyCoarsePoints) else q.coarse,
            )
        }
        return out
    }

    // ----- News -----

    private fun newsStep(seed: Long, mk: MarketState, t: Long, events: MutableList<GameEvent>): MarketState {
        if (m.news.isEmpty()) return mk
        val r = rng(seed, "news", t)
        if (!r.chance(cfg.newsChancePerMinute)) return mk
        val tpl = r.pickWeighted(m.news) { it.weight.toDouble() }
        val effect = r.nextDouble(tpl.min, tpl.max)
        var quotes = mk.quotes
        var sectorDrift = mk.sectorDrift
        var hints = mk.hints
        var reliability = ""
        val (target, name) = when (tpl.kind) {
            "company", "hint" -> m.stocks[r.nextInt(m.stocks.size)].let { it.id to it.name }
            "sector" -> m.sectors[r.nextInt(m.sectors.size)].let { it.id to it.name.lowercase() }
            "crypto" -> m.coins[r.nextInt(m.coins.size)].let { it.id to it.name }
            "commodity" -> (if (tpl.only.isEmpty()) m.commodities else m.commodities.filter { it.id in tpl.only }).let { list ->
                list[r.nextInt(list.size)].let { it.id to it.name.lowercase() }
            }
            else -> return mk
        }
        var applied = effect
        when (tpl.kind) {
            "company" -> quotes = quotes + (target to quotes.getValue(target).let { q ->
                // Half of a story is permanent (fundamentals), half fades as the noise reverts.
                q.copy(logV = q.logV + 0.5 * ln(1 + effect), x = q.x + 0.5 * ln(1 + effect))
            })
            "commodity" -> quotes = quotes + (target to quotes.getValue(target).let { q -> q.copy(x = q.x + ln(1 + effect)) })
            "crypto" -> quotes = quotes + (target to quotes.getValue(target).let { q ->
                if (tpl.id == "cr_hype") q.copy(hypeDrift = effect * 2, hypeUntil = t + 12 * 60) else q.copy(logV = q.logV + ln(1 + effect))
            })
            "sector" -> sectorDrift = sectorDrift + (target to SectorDrift(effect * 4, t + 6 * 60)).also { applied = effect }
            "hint" -> {
                reliability = r.pickWeighted(listOf("Rumor" to 0.5, "Report" to 0.35, "Confirmed" to 0.15)) { it.second }.first
                hints = (hints.filterNot { it.stockId == target } + EarningsHint(target, effect, reliability, t))
                applied = 0.0
            }
        }
        val text = fill(tpl, name)
        val headline = if (reliability.isNotEmpty()) "$reliability: $text" else text
        val item = NewsItem(mk.nextNewsId, t, headline, tpl.kind, target, applied, reliability)
        events += GameEvent.MarketNews(item.id)
        return mk.copy(quotes = quotes, sectorDrift = sectorDrift, hints = hints, news = addNews(mk.news, item), nextNewsId = mk.nextNewsId + 1)
    }

    private fun fill(tpl: NewsTemplateDef, name: String) = tpl.text
        .replace("{company}", name).replace("{sector}", name).replace("{coin}", name).replace("{commodity}", name)
        .replace("s's ", "s' ")
        .replaceFirstChar { it.uppercase() }

    private fun addNews(list: List<NewsItem>, item: NewsItem): List<NewsItem> = (list + item).takeLast(cfg.newsKept)

    // ----- Earnings -----

    private fun earnings(state: GameState, mk: MarketState, t: Long, events: MutableList<GameEvent>): MarketState {
        val r = rng(state.worldSeed, "earnings", t)
        val quotes = mk.quotes.toMutableMap()
        val surprises = ArrayList<Pair<String, Double>>(m.stocks.size)
        val hintBy = mk.hints.associateBy { it.stockId }
        for (s in m.stocks) {
            var z = r.nextGaussian() * cfg.earningsSurpriseSd
            hintBy[s.id]?.let { h ->
                val rel = cfg.hintReliability[h.reliability] ?: 0.5
                z += if (r.chance(rel)) h.magnitude else -0.5 * h.magnitude
            }
            z = z.coerceIn(-0.5, 0.6)
            val q = quotes.getValue(s.id)
            quotes[s.id] = q.copy(logV = q.logV + ln(1 + z))
            surprises += s.id to z
        }
        var news = mk.news
        var nextId = mk.nextNewsId
        val beats = surprises.count { it.second > 0 }
        news = addNews(news, NewsItem(nextId++, t, "Earnings season: $beats companies beat estimates, ${surprises.size - beats} missed.", "earnings"))
        val notable = (surprises.sortedByDescending { abs(it.second) }.take(3) +
            surprises.filter { it.first in state.market.holdings || it.first in state.market.shorts || it.first in hintBy }.filter { abs(it.second) > 0.02 })
            .distinctBy { it.first }.take(6)
        for ((id, z) in notable) {
            val name = m.stockById.getValue(id).name
            val verb = if (z > 0) "beats estimates" else "misses estimates"
            news = addNews(news, NewsItem(nextId++, t, "$name $verb; shares ${signedPct(z)}", "earnings", id, z))
        }
        events += GameEvent.EarningsSeason(beats, surprises.size - beats)
        return mk.copy(quotes = quotes, news = news, nextNewsId = nextId, hints = emptyList())
    }

    // ----- Payouts: dividends, coupons, staking, borrow fees, bond defaults -----

    private fun payouts(state: GameState, t: Long, events: MutableList<GameEvent>): GameState {
        var s = state
        val share = cfg.payoutEveryHours / 24.0
        val time = t * 60_000
        var ledger = s.ledger
        var dividends = Money.ZERO
        var coupons = Money.ZERO
        var staking = Money.ZERO
        for (id in s.market.holdings.keys.sorted()) {
            val value = math.holdingValue(s, id)
            if (!value.isPositive) continue
            val perDay = when (math.assetClass(id)) {
                AssetClass.Stock -> m.stockById.getValue(id).dividendPerDay
                AssetClass.Fund -> fundDividendPerDay.getValue(id)
                AssetClass.Bond -> couponPerDay(s, id)
                AssetClass.Crypto -> m.coinById.getValue(id).stakingPerDay
                else -> 0.0
            }
            if (perDay <= 0) continue
            val boost = if (math.assetClass(id) == AssetClass.Stock || math.assetClass(id) == AssetClass.Fund) 1 + dividendBonus(s) else 1.0
            val amount = value * Multiplier.ofDouble(perDay * share * boost)
            when (math.assetClass(id)) {
                AssetClass.Bond -> coupons += amount
                AssetClass.Crypto -> staking += amount
                else -> dividends += amount
            }
        }
        if (dividends.isPositive) ledger = Ledger.earn(ledger, time, "dividends", dividends)
        if (coupons.isPositive) ledger = Ledger.earn(ledger, time, "coupons", coupons)
        if (staking.isPositive) ledger = Ledger.earn(ledger, time, "staking", staking)
        val earned = dividends + coupons + staking
        if (earned.isPositive) events += GameEvent.MarketPayout(earned)
        // Borrow fees on shorts: from cash, or from the position's collateral when cash is short.
        var shorts = s.market.shorts
        for (id in shorts.keys.sorted()) {
            val sp = shorts.getValue(id)
            val fee = math.shortLiability(s, id) * Multiplier.ofDouble(cfg.borrowFeePerDay * share)
            if (!fee.isPositive) continue
            val fromCash = fee.min(ledger.cash.max(Money.ZERO))
            val fromCollateral = (fee - fromCash).min(sp.collateral)
            val postings = buildList {
                if (fromCash.isPositive) add(Posting(Accounts.CASH, -fromCash))
                if (fromCollateral.isPositive) add(Posting(COLLATERAL, -fromCollateral))
                add(Posting(Accounts.expense("borrow_fees"), fromCash + fromCollateral))
            }
            val r = Ledger.post(ledger, time, TxnCategory.Investment, "borrow fee ${tickerOf(id)}", postings)
            if (r is Ledger.Result.Ok) {
                ledger = r.state
                if (fromCollateral.isPositive) shorts = shorts + (id to sp.copy(collateral = sp.collateral - fromCollateral))
            }
        }
        s = s.copy(ledger = ledger, market = s.market.copy(shorts = shorts))
        // High-yield bonds may default during a recession: holders keep 40 % until the issuer is restructured.
        if (s.market.macro.phase == "recession") {
            val r = rng(s.worldSeed, "bond_default", t)
            for (b in m.bonds) {
                if (b.defaultChance <= 0 || !r.chance(b.defaultChance)) continue
                val q = s.market.quotes.getValue(b.id)
                val mk = s.market
                val item = NewsItem(mk.nextNewsId, t, "${b.name}: the issuer defaults. Bondholders expect to recover about 40%.", "bond", b.id, -0.6)
                s = s.copy(market = mk.copy(quotes = mk.quotes + (b.id to q.copy(x = q.x + ln(0.4))), news = addNews(mk.news, item), nextNewsId = mk.nextNewsId + 1))
            }
        }
        return s
    }

    private fun marginCalls(state: GameState, t: Long, events: MutableList<GameEvent>): GameState {
        var s = state
        for (id in state.market.shorts.keys.sorted()) {
            val sp = s.market.shorts[id] ?: continue
            val liability = math.shortLiability(s, id)
            if (sp.collateral.toDollarsDouble() < cfg.shortMaintenanceMargin * liability.toDollarsDouble()) {
                val r = Trading(this).cover(s, id, forced = true, events = events)
                if (r is Trading.Result.Ok) s = r.state
            }
        }
        return s
    }

    /** When the story reaches a chapter with deeper markets, companies issue shares and stakes are diluted. */
    private fun dilution(state: GameState, t: Long): GameState {
        val ch = state.progress.chapter
        val mk = state.market
        if (ch <= mk.depthChapter) return state
        val before = math.depthScale(mk.depthChapter)
        val after = math.depthScale(ch)
        if (after <= before) return state.copy(market = mk.copy(depthChapter = ch))
        val factor = after / before
        val item = NewsItem(mk.nextNewsId, t, "Markets deepen as the economy grows: listed companies issue new shares (×${fmtFactor(factor)}). Existing stakes are diluted.", "macro")
        return state.copy(market = mk.copy(depthChapter = ch, news = addNews(mk.news, item), nextNewsId = mk.nextNewsId + 1))
    }

    // ----- Initialisation: quotes start at their listed price with a week of seeded history -----

    private fun initialize(state: GameState, minute: Long): GameState {
        val quotes = LinkedHashMap<String, Quote>()
        val coarseDt = cfg.historyCoarseMinutes.toDouble() / MINUTES_PER_DAY
        val fineDt = cfg.historyFineMinutes.toDouble() / MINUTES_PER_DAY
        fun history(id: String, start: Double, vol: Double, drift: Double, revert: Double = 0.0): Triple<List<Int>, List<Int>, Double> {
            // Walk backwards from today's price so the series ends exactly at the listed price.
            val r = rng(state.worldSeed, "warmup:$id", 0)
            val fine = ArrayList<Int>(cfg.historyFinePoints)
            val coarse = ArrayList<Int>(cfg.historyCoarsePoints)
            var lp = ln(start)
            val anchor = ln(start)
            repeat(cfg.historyFinePoints) {
                fine += PriceHistory.key(exp(lp))
                lp -= drift * fineDt + vol * sqrt(fineDt) * r.nextGaussian() + revert * (lp - anchor) * fineDt
            }
            repeat(cfg.historyCoarsePoints) {
                coarse += PriceHistory.key(exp(lp))
                lp -= drift * coarseDt + vol * sqrt(coarseDt) * r.nextGaussian() + revert * (lp - anchor) * coarseDt
            }
            return Triple(fine.reversed(), coarse.reversed(), ln(start))
        }
        m.stocks.forEach { s ->
            val (f, c, lv) = history(s.id, s.startPrice.toDouble(), s.volPerDay, ln(1 + s.driftPerDay))
            quotes[s.id] = Quote(logV = lv, fine = f, coarse = c)
        }
        m.funds.forEach { fd ->
            val vol = when (fd.style) { "growth" -> 0.022; "defensive" -> 0.009; else -> 0.013 }
            val (f, c, lv) = history(fd.id, 100.0, vol, 0.012)
            quotes[fd.id] = Quote(logV = lv, fine = f, coarse = c)
        }
        m.bonds.forEach { b ->
            val (f, c, lv) = history(b.id, 100.0, 0.002 + b.duration * 0.0004, 0.0, revert = 1.0)
            quotes[b.id] = Quote(logV = lv, fine = f, coarse = c)
        }
        m.coins.forEach { co ->
            val (f, c, lv) = history(co.id, co.startPrice.toDouble(), co.volPerDay, ln(1 + co.driftPerDay))
            quotes[co.id] = Quote(logV = lv, fine = f, coarse = c)
        }
        m.commodities.forEach { co ->
            val (f, c, lv) = history(co.id, co.anchorPrice.toDouble(), co.volPerDay, 0.0, revert = co.reversionPerDay)
            quotes[co.id] = Quote(logV = lv, fine = f, coarse = c)
        }
        val r = rng(state.worldSeed, "macro", -1)
        val first = m.macro.firstOrNull { it.id == "expansion" } ?: m.macro.firstOrNull()
        val macro = if (first == null) MacroState() else MacroState(first.id, minute + (r.nextDouble(first.minDays, first.maxDays) * MINUTES_PER_DAY).toLong())
        // Bonds start at the price implied by the opening rate.
        val rate = first?.ratePerDay ?: refRate
        m.bonds.forEach { b ->
            val q = quotes.getValue(b.id)
            quotes[b.id] = q.copy(logV = ln(100.0) - b.duration * BOND_RATE_SENSITIVITY * (rate - refRate))
        }
        val welcome = NewsItem(1, minute, "Markets open for the day. Prices move every minute; news and earnings move them more.", "macro")
        return state.copy(market = state.market.copy(tick = minute, macro = macro, quotes = quotes, news = listOf(welcome), nextNewsId = 2, depthChapter = state.progress.chapter))
    }

    fun tickerOf(id: String) = m.instrumentById[id]?.ticker ?: id

    companion object {
        const val MINUTES_PER_DAY = 1440L
        /** How strongly bond prices react to the rate (per unit of duration). */
        const val BOND_RATE_SENSITIVITY = 2.0
        const val INVESTMENTS = "asset:investments"
        const val COLLATERAL = "asset:collateral"
        const val SHORT_LIABILITY = "liability:shorts"

        fun pct(x: Double, decimals: Int = 1): String = "%.${decimals}f%%".format(java.util.Locale.ROOT, x * 100)
        fun signedPct(x: Double): String = (if (x >= 0) "+" else "−") + pct(abs(x))
        private fun fmtFactor(f: Double): String = if (f >= 1000) "%,.0f".format(java.util.Locale.ROOT, f) else "%.0f".format(java.util.Locale.ROOT, f)
    }
}
