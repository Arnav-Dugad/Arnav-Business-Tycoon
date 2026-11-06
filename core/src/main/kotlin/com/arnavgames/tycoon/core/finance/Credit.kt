package com.arnavgames.tycoon.core.finance

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.content.RatingDef
import com.arnavgames.tycoon.core.engine.Economy
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState

/**
 * Pure credit and tax formulas (docs/ECONOMY.md §8): credit score and rating, borrowing limits, loan pricing, the
 * progressive income-tax schedule and the deductions a player has earned.
 */
class Credit(private val content: ContentPack, private val economy: Economy) {
    val cfg get() = content.world.config.finance

    // ----- Debt -----

    fun loanDebt(state: GameState): Money = state.finance.loans.fold(Money.ZERO) { a, l -> a + l.principal } + state.finance.creditLine

    fun totalDebt(state: GameState): Money = loanDebt(state) + economy.estate.debt(state)

    /** Net business income per hour (after upkeep, before tax). */
    fun hourlyIncome(state: GameState): Money = economy.totalNetRate(state).over(3600)

    // ----- Score and rating -----

    fun dtiPoints(state: GameState): Int {
        val debt = totalDebt(state)
        if (!debt.isPositive) return 0
        val daily = hourlyIncome(state).toDollarsDouble() * 24
        val days = if (daily <= 0) Double.MAX_VALUE else debt.toDollarsDouble() / daily
        return cfg.dti.firstOrNull { days <= it.days }?.points ?: (cfg.dti.lastOrNull()?.points ?: 0)
    }

    fun score(state: GameState): Int = (state.finance.scoreHistory + dtiPoints(state) + economy.dynasty.bonus(state, "creditScore").toInt()).coerceIn(cfg.scoreMin, cfg.scoreMax)

    fun rating(state: GameState): RatingDef = ratingFor(score(state))

    fun ratingFor(score: Int): RatingDef = cfg.ratings.firstOrNull { score >= it.min } ?: cfg.ratings.lastOrNull() ?: DEFAULT_RATING

    /** Central-bank rate (macro phase) plus the rating's spread, per day. */
    fun loanRatePerDay(state: GameState): Double = economy.centralRate(state) + rating(state).spreadPerDay

    /** Extra mortgage spread relative to a BBB borrower (half the loan-spread gap). */
    fun mortgageSpreadAdjustment(state: GameState): Double {
        val bbb = cfg.ratings.firstOrNull { it.name == "BBB" }?.spreadPerDay ?: return 0.0
        return (rating(state).spreadPerDay - bbb) * 0.5
    }

    fun maxLtv(state: GameState): Double = if (cfg.ratings.isEmpty()) 1.0 else rating(state).maxLtv

    /** How much more can be borrowed as a term loan right now. */
    fun borrowLimit(state: GameState): Money {
        val r = rating(state)
        if (r.borrowFactor <= 0) return Money.ZERO
        val byIncome = hourlyIncome(state) * Multiplier.ofDouble(cfg.borrowIncomeHours * r.borrowFactor)
        val byWealth = economy.netWorth(state).max(Money.ZERO) * Multiplier.ofDouble(cfg.borrowNetWorthShare)
        return (byIncome.min(byWealth) - loanDebt(state)).max(Money.ZERO)
    }

    fun creditLineLimit(state: GameState): Money {
        val r = rating(state)
        if (r.borrowFactor <= 0) return Money.ZERO
        return hourlyIncome(state) * Multiplier.ofDouble(cfg.creditLineIncomeHours * r.borrowFactor)
    }

    /** Equal 6-hourly payments that repay [principal] over [days] at [ratePerDay]. */
    fun installment(principal: Money, ratePerDay: Double, days: Int): Money {
        val n = days * 4
        val i = ratePerDay / 4
        val factor = if (i <= 0) 1.0 / n else i / (1 - Math.pow(1 + i, -n.toDouble()))
        return principal * Multiplier.ofDouble(round6up(factor))
    }

    /** What every loan and the credit line cost per 6 hours right now. */
    fun debtService6h(state: GameState): Money {
        val f = state.finance
        val lineInterest = f.creditLine * Multiplier.ofDouble(loanRatePerDay(state) / 4)
        return f.loans.fold(lineInterest) { a, l -> a + l.installment }
    }

    fun canRestructure(state: GameState): Boolean =
        state.finance.loans.isNotEmpty() && debtService6h(state) > hourlyIncome(state) * 6L

    fun phoenixAvailable(state: GameState): Boolean {
        val since = state.finance.negativeSince
        return since >= 0 && state.simTimeMs - since >= cfg.phoenixNegativeMinutes * 60_000L
    }

    // ----- Taxes -----

    /** Lifetime business income (net of upkeep, before tax) from the ledger. */
    fun businessIncomeTotal(state: GameState): Money =
        content.businesses.fold(Money.ZERO) { a, b -> a - state.ledger.balance(Accounts.revenue(b.id)) }

    fun taxWithheldTotal(state: GameState): Money = state.ledger.balance(Accounts.expense(INCOME_TAX))

    /** Effective rate of the progressive schedule on an average income of [perHour] (marginal brackets). */
    fun effectiveRate(perHour: Money): Double {
        val h = perHour.toDollarsDouble()
        if (h <= 0 || cfg.taxBrackets.isEmpty()) return cfg.taxBrackets.firstOrNull()?.rate ?: 0.0
        var lower = 0.0
        var tax = 0.0
        for (b in cfg.taxBrackets) {
            val upper = b.upToPerHour?.toDollarsDouble() ?: Double.MAX_VALUE
            if (h <= lower) break
            tax += (minOf(h, upper) - lower) * b.rate
            lower = upper
        }
        return round6(tax / h)
    }

    fun marginalRate(perHour: Money): Double {
        val h = perHour.toDollarsDouble()
        return cfg.taxBrackets.firstOrNull { b -> b.upToPerHour == null || h <= b.upToPerHour.toDollarsDouble() }?.rate ?: 0.0
    }

    /** Deductions earned this quarter, each as a share of the tax withheld (GDD §11: you earn them through play). */
    fun deductions(state: GameState, withheld: Money): Map<String, Double> {
        val d = cfg.deductions
        val out = LinkedHashMap<String, Double>()
        val ownsRd = cfg.rdBusinesses.any { it in state.businesses }
        val rdPool = if (withheld.isPositive) (state.world.rdThisQuarter.toDollarsDouble() / withheld.toDollarsDouble()).coerceAtMost(d.rdPoolCap) else 0.0
        val rd = (if (ownsRd) d.rd else 0.0) + rdPool
        if (rd > 0) out["rd"] = round6(rd)
        val props = state.estate.properties.size
        if (props > 0) out["depreciation"] = round6((props * d.depreciationPerProperty).coerceAtMost(d.depreciationMax))
        if (withheld.isPositive && state.world.donationsThisQuarter.isPositive) {
            out["donations"] = round6((state.world.donationsThisQuarter.toDollarsDouble() / withheld.toDollarsDouble()).coerceAtMost(d.donationCap))
        }
        if (cfg.greenBusinesses.any { it in state.businesses }) out["green"] = d.green
        // Cap the total at the maximum share, trimming the last items first.
        var left = d.max
        return out.mapValues { (_, v) -> val take = minOf(v, left); left -= take; round6(take) }.filterValues { it > 0 }
    }

    companion object {
        const val INCOME_TAX = "income_tax"
        val DEFAULT_RATING = RatingDef("BBB", 0, 0.008, 1.0, 0.6)
        fun round6(x: Double): Double = kotlin.math.floor(x * 1_000_000.0 + 0.5) / 1_000_000.0
        private fun round6up(x: Double): Double = kotlin.math.ceil(x * 1_000_000.0) / 1_000_000.0
    }
}
