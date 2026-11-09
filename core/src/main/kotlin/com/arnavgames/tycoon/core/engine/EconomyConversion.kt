package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.market.MarketEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneySerializer
import com.arnavgames.tycoon.core.save.SaveCodec
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MarketState
import java.math.BigDecimal
import java.math.MathContext

/**
 * Converts a save from the idle-game economy (version 1, money climbing to 10^17+) to real-world money (version 2,
 * owner decision 2026-11-09). Progress is kept: chapter, story, businesses and their levels, managers, expansions,
 * headquarters, mergers, properties and the collection. Money is rescaled.
 *
 * 1. Market positions are sold at today's prices (no fee, no tax). Companies now have their real size, so old
 *    positions could be far above the 49% ownership limit.
 * 2. The player's scale factor f: how much their own businesses shrank. Every business's prices moved by a known
 *    factor (new unlock cost / old unlock cost), so f = (value of their businesses now) / (value before).
 *    Early businesses didn't change, so early players are barely touched.
 * 3. Every money amount in the save is multiplied by f (by decoding the save once with a scaling Money reader, so
 *    nothing is missed). Bullion and Favor are not money and are restored exactly. The ledger is re-balanced to the
 *    cent against the world account.
 */
object EconomyConversion {
    const val CURRENT = 2

    /** The idle-game unlock costs (content before 1.8.0), to recover each business's price factor. */
    private val OLD_UNLOCK: Map<String, BigDecimal> = mapOf(
        "factory" to "15000000", "fashion" to "150000000", "mining" to "1725000000", "energy" to "19837500000",
        "hotels" to "228131250000", "bank" to "821272500000", "construction" to "2956581000000", "media" to "10643691600000",
        "tech" to "38317289760000", "telecom" to "137942243136000", "airline" to "496592075289600", "pharma" to "1787731471042560",
        "sports" to "6435833295753216", "space" to "23168999864711577.6",
    ).mapValues { BigDecimal(it.value) }

    /** The most a converted save may be worth, by chapter (real fortunes: $1M in chapter 1 … $1T at the very top). */
    private val CHAPTER_CAP = listOf("1000000", "100000000", "5000000000", "50000000000", "100000000000", "200000000000", "400000000000", "1000000000000")

    private const val MIN_KEPT_LEVEL = 50

    fun needed(s: GameState): Boolean = s.meta.economy < CURRENT

    /** The factor that maps this player's old money to real-world money (1.0 for early players). */
    fun factor(engine: GameEngine, s: GameState): BigDecimal {
        val mc = MathContext.DECIMAL128
        var now = BigDecimal.ZERO
        var before = BigDecimal.ZERO
        for (b in s.businesses.values) {
            val def = engine.content.businessById[b.id] ?: continue
            val value = BigDecimal(engine.economy.investedIn(def, b.level).cents)
            val old = OLD_UNLOCK[b.id]
            val c = if (old == null) BigDecimal.ONE else BigDecimal(def.unlockCost.cents).movePointLeft(2).divide(old, mc)
            now += value
            before += value.divide(c, mc)
        }
        if (before.signum() <= 0) return BigDecimal.ONE
        return now.divide(before, mc).min(BigDecimal.ONE)
    }

    fun convert(engine: GameEngine, s0: GameState): GameState {
        if (!needed(s0)) return s0
        // 1. Sell every market position at today's price, straight to cash.
        var s = s0
        var proceeds = Money.ZERO
        for ((id, h) in s.market.holdings) {
            val p = engine.market.math.priceMicros(s, id) ?: continue
            proceeds += h.qty.valueAt(p)
        }
        proceeds += engine.market.math.shortEquity(s)
        val invested = s.ledger.balance(MarketEngine.INVESTMENTS)
        if (proceeds.isPositive || !invested.isZero) {
            val postings = buildList {
                if (proceeds.isPositive) add(com.arnavgames.tycoon.core.ledger.Posting(Accounts.CASH, proceeds))
                if (!invested.isZero) add(com.arnavgames.tycoon.core.ledger.Posting(MarketEngine.INVESTMENTS, -invested))
                val rest = proceeds - invested
                if (!rest.isZero) add(com.arnavgames.tycoon.core.ledger.Posting(Accounts.WORLD, -rest))
            }
            val posted = Ledger.post(s.ledger, s.simTimeMs, com.arnavgames.tycoon.core.ledger.TxnCategory.Investment, "positions converted to cash (1.8.0)", postings, floors = emptyMap())
            if (posted is Ledger.Result.Ok) s = s.copy(ledger = posted.state)
        }
        // The recent-transactions list (UI history) is cleared: scaling each posting separately would round it out of
        // balance, and its old amounts would read as nonsense next to the new ones.
        s = s.copy(market = s.market.copy(holdings = emptyMap(), shorts = emptyMap()), ledger = s.ledger.copy(recent = emptyList()))

        // 2–3. Rescale all money. Saves with far more cash than their businesses explain (cheats, debug grants) are
        // capped at a realistic fortune for their chapter, so nobody starts the new economy above real-world wealth.
        val mc = MathContext.DECIMAL128
        val cap = BigDecimal(CHAPTER_CAP.getOrElse(s.progress.chapter - 1) { CHAPTER_CAP.last() })
        fun valueOf(st: GameState) = st.businesses.values.fold(Money.ZERO) { a, b ->
            if (engine.content.businessById[b.id] == null) a else a + engine.economy.businessValue(st, b.id)
        }
        // Extreme idle-game levels (hundreds) on the big businesses (chapter 3 on) would be worth trillions at real
        // prices. They keep a level that fits a real fortune for the chapter, never below Scale Tier 3 (level 50).
        // The small first-chapter shops are worth little at any level and keep theirs.
        if (BigDecimal(valueOf(s).cents).movePointLeft(2) > cap.multiply(BigDecimal("0.8"))) {
            val big = s.businesses.values.filter { (engine.content.businessById[it.id]?.chapter ?: 0) >= 3 }.map { it.id }.toSet()
            fun clamp(l: Int) = s.copy(businesses = s.businesses.mapValues { (id, b) -> if (id in big && b.level > l) b.copy(level = l) else b })
            var lo = MIN_KEPT_LEVEL
            var hi = s.businesses.values.filter { it.id in big }.maxOfOrNull { it.level } ?: MIN_KEPT_LEVEL
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (BigDecimal(valueOf(clamp(mid)).cents).movePointLeft(2) <= cap.multiply(BigDecimal("0.8"))) lo = mid else hi = mid - 1
            }
            s = clamp(lo)
        }
        val bizValue = valueOf(s)
        val moneyPart = BigDecimal((engine.economy.netWorth(s) - bizValue).cents).movePointLeft(2)
        val room = cap - BigDecimal(bizValue.cents).movePointLeft(2)
        var f = factor(engine, s)
        if (moneyPart.signum() > 0 && moneyPart.multiply(f) > room) f = room.max(BigDecimal.ZERO).divide(moneyPart, mc)
        val premium = listOf(Accounts.BULLION, Accounts.FAVOR, Accounts.WORLD_PREMIUM).associateWith { s.ledger.balance(it) }
        val scaled = if (f.compareTo(BigDecimal.ONE) == 0) s else {
            val text = SaveCodec.json.encodeToString(GameState.serializer(), s)
            MoneySerializer.scaleOnRead.set(f)
            try { SaveCodec.json.decodeFromString(GameState.serializer(), text) } finally { MoneySerializer.scaleOnRead.remove() }
        }
        var balances = scaled.ledger.balances + premium.filterValues { !it.isZero }
        // Re-balance the rounding (a few cents) against the world account so the books stay exact.
        val residue = balances.values.fold(Money.ZERO) { a, b -> a + b }
        if (!residue.isZero) balances = balances + (Accounts.WORLD to ((balances[Accounts.WORLD] ?: Money.ZERO) - residue))
        return scaled.copy(
            ledger = scaled.ledger.copy(balances = balances),
            meta = scaled.meta.copy(economy = CURRENT),
        )
    }
}
