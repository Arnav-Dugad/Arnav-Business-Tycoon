package com.arnavgames.tycoon.core.finance

import com.arnavgames.tycoon.core.content.ContentPack
import com.arnavgames.tycoon.core.engine.Economy
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerError
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.market.Trading
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.random.GameRandom
import com.arnavgames.tycoon.core.state.DeskEntry
import com.arnavgames.tycoon.core.state.FinanceState
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.LoanState
import com.arnavgames.tycoon.core.state.ModifierState
import com.arnavgames.tycoon.core.state.TaxFiling
import com.arnavgames.tycoon.core.state.TaxState
import com.arnavgames.tycoon.core.state.TimerKind

/**
 * Finance pressure (docs/GDD.md §11, docs/ECONOMY.md §8): term loans and a credit line repaid every 6 hours, the
 * credit score, quarterly income tax (withheld as income is earned; self-filing claims earned deductions), and the
 * never-stuck guarantees: restructuring and the Phoenix protocol.
 *
 * Everything here happens on minute ticks or commands, so offline catch-up equals live play.
 */
class FinanceEngine(private val content: ContentPack, private val economy: Economy, private val trading: Trading, private val cgt: Double) {
    val credit get() = economy.credit
    private val cfg get() = content.world.config.finance
    private val world get() = economy.world

    sealed interface Result {
        data class Ok(val state: GameState) : Result
        data class Err(val error: GameError) : Result
    }

    private fun err(e: GameError) = Result.Err(e)

    private fun withFinance(s: GameState, f: FinanceState) = s.copy(finance = f)

    private fun adjustScore(s: GameState, points: Int): GameState =
        withFinance(s, s.finance.copy(scoreHistory = (s.finance.scoreHistory + points).coerceIn(cfg.scoreMin, cfg.scoreMax)))

    fun applyScore(s: GameState, points: Int) = adjustScore(s, points)

    private fun log(s: GameState, title: String, text: String, good: Boolean?): GameState =
        s.copy(world = s.world.copy(log = (s.world.log + DeskEntry(s.simTimeMs, title, text, good)).takeLast(30)))

    private fun post(s: GameState, category: TxnCategory, memo: String, postings: List<Posting>, floors: Map<String, Money> = Ledger.DEFAULT_FLOORS): Pair<GameState?, GameError?> =
        when (val r = Ledger.post(s.ledger, s.simTimeMs, category, memo, postings, floors)) {
            is Ledger.Result.Ok -> s.copy(ledger = r.state) to null
            is Ledger.Result.Err -> null to when (val e = r.error) {
                is LedgerError.InsufficientFunds -> GameError.InsufficientFunds(e.needed, e.available)
            }
        }

    // =====================================================================================
    // Commands
    // =====================================================================================

    fun takeLoan(s: GameState, amount: Money, days: Int, events: MutableList<GameEvent>): Result {
        if (!world.isUnlocked(s, "loans")) return err(GameError.Locked("Loans open in chapter ${world.unlockChapter("loans")}"))
        if (days !in cfg.loanTermsDays) return err(GameError.Invalid("Unsupported loan term"))
        if (!amount.isPositive) return err(GameError.Invalid("Choose an amount"))
        val limit = credit.borrowLimit(s)
        if (amount > limit) return err(GameError.Invalid(if (limit.isPositive) "Your limit is lower than that" else "Your credit rating does not allow more borrowing"))
        val rate = credit.loanRatePerDay(s)
        val loan = LoanState(s.finance.nextLoanId, amount, credit.installment(amount, rate, days), days * 4, rate, s.simTimeMs, amount)
        val (ps, e) = post(s, TxnCategory.Loan, "term loan", listOf(Posting(Accounts.CASH, amount), Posting(LOANS, -amount)))
        if (ps == null) return err(e!!)
        events += GameEvent.LoanTaken(loan.id, amount)
        return Result.Ok(withFinance(ps, ps.finance.copy(loans = ps.finance.loans + loan, nextLoanId = loan.id + 1)))
    }

    fun repayLoan(s: GameState, loanId: Long, events: MutableList<GameEvent>): Result {
        val l = s.finance.loans.firstOrNull { it.id == loanId } ?: return err(GameError.NotFound("loan"))
        val (ps, e) = post(s, TxnCategory.Loan, "repay loan", listOf(Posting(Accounts.CASH, -l.principal), Posting(LOANS, l.principal)))
        if (ps == null) return err(e!!)
        events += GameEvent.LoanRepaid(loanId)
        return Result.Ok(withFinance(ps, ps.finance.copy(loans = ps.finance.loans - l)))
    }

    fun drawCredit(s: GameState, amount: Money): Result {
        if (!world.isUnlocked(s, "loans")) return err(GameError.Locked("The credit line opens in chapter ${world.unlockChapter("loans")}"))
        if (!amount.isPositive) return err(GameError.Invalid("Choose an amount"))
        if (s.finance.creditLine + amount > credit.creditLineLimit(s)) return err(GameError.Invalid("That is over your credit line"))
        val (ps, e) = post(s, TxnCategory.Loan, "credit line draw", listOf(Posting(Accounts.CASH, amount), Posting(CREDIT_LINE, -amount)))
        if (ps == null) return err(e!!)
        return Result.Ok(withFinance(ps, ps.finance.copy(creditLine = ps.finance.creditLine + amount)))
    }

    fun repayCredit(s: GameState, amount: Money): Result {
        val pay = amount.min(s.finance.creditLine)
        if (!pay.isPositive) return err(GameError.Invalid("Nothing to repay"))
        val (ps, e) = post(s, TxnCategory.Loan, "credit line repayment", listOf(Posting(Accounts.CASH, -pay), Posting(CREDIT_LINE, pay)))
        if (ps == null) return err(e!!)
        return Result.Ok(withFinance(ps, ps.finance.copy(creditLine = ps.finance.creditLine - pay)))
    }

    fun restructure(s: GameState, events: MutableList<GameEvent>): Result {
        if (!credit.canRestructure(s)) return err(GameError.Locked("Restructuring is offered when debt payments exceed your income"))
        val loans = s.finance.loans.map { l ->
            val n = l.paymentsLeft * 2
            l.copy(paymentsLeft = n, installment = credit.installment(l.principal, l.ratePerDay, (n + 3) / 4).max(Money.ofCents(1)))
        }
        events += GameEvent.Restructured
        val ns = withFinance(s, s.finance.copy(loans = loans, restructures = s.finance.restructures + 1))
        return Result.Ok(log(adjustScore(ns, cfg.restructurePoints), "Loans restructured", "Your loan terms are twice as long and payments smaller. Your credit rating drops.", false))
    }

    /**
     * The Phoenix protocol: sells every investment, property and collectible, clears all debts (the bank writes off any
     * shortfall), keeps 10 % of business levels plus managers and expansions, and grants a 2-hour ×3 comeback boost.
     */
    fun acceptPhoenix(s0: GameState, events: MutableList<GameEvent>): Result {
        if (!credit.phoenixAvailable(s0)) return err(GameError.Locked("Only offered after net worth has been negative for ${cfg.phoenixNegativeMinutes} minutes"))
        var s = s0
        for (id in s.market.shorts.keys.sorted()) s = (trading.cover(s, id, forced = true, events = events) as? Trading.Result.Ok)?.state ?: s
        for (id in s.market.holdings.keys.sorted()) s = (trading.sell(s, id, 1000, events) as? Trading.Result.Ok)?.state ?: s
        s = economy.estate.liquidateAll(s, cgt, events)
        for (id in s.luxury.owned.keys.sorted()) s = (economy.luxury.sell(s, id, events) as? com.arnavgames.tycoon.core.luxury.LuxuryEngine.Result.Ok)?.state ?: s
        s = s.copy(luxury = s.luxury.copy(auction = null))
        // Debts: repay what cash covers, write off the rest.
        val debt = credit.loanDebt(s)
        if (debt.isPositive) {
            val paid = debt.min(s.cash.max(Money.ZERO))
            val loansTotal = s.finance.loans.fold(Money.ZERO) { a, l -> a + l.principal }
            val (ps, _) = post(s, TxnCategory.Loan, "Phoenix: debts settled", listOf(
                Posting(Accounts.CASH, -paid), Posting(LOANS, loansTotal), Posting(CREDIT_LINE, s.finance.creditLine),
                Posting(Accounts.revenue("debt_written_off"), -(debt - paid)),
            ), emptyMap())
            s = ps!!
        }
        val businesses = s.businesses.mapValues { (_, b) ->
            b.copy(level = maxOf(1, (b.level * cfg.phoenixKeepLevels).toInt()), stored = Money.ZERO, accrual = com.arnavgames.tycoon.core.money.Accrual())
        }
        val boost = ModifierState("phoenix", "all", cfg.phoenixBoost - 1.0, s.simTimeMs + cfg.phoenixBoostHours * 3_600_000L, "Comeback boost")
        s = s.copy(
            businesses = businesses,
            timers = s.timers.filter { it.kind == TimerKind.Expansion },
            finance = s.finance.copy(loans = emptyList(), creditLine = Money.ZERO, negativeSince = -1, phoenixUsed = s.finance.phoenixUsed + 1,
                scoreHistory = cfg.phoenixScore),
            world = s.world.copy(modifiers = s.world.modifiers + boost),
        )
        events += GameEvent.PhoenixReborn
        return Result.Ok(log(s, "Phoenix protocol", "Everything was sold in an orderly way and your debts are gone. Your businesses kept their managers, and a ×${cfg.phoenixBoost.toInt()} comeback boost runs for ${cfg.phoenixBoostHours} hours.", true))
    }

    fun setAutoFile(s: GameState, on: Boolean): Result = Result.Ok(s.copy(finance = s.finance.copy(tax = s.finance.tax.copy(autoFile = on))))

    fun fileTaxes(s: GameState, aggressive: Boolean, events: MutableList<GameEvent>): Result {
        val f = s.finance.tax.pending ?: return err(GameError.Invalid("No tax return is due"))
        return Result.Ok(file(s, f, selfFile = true, aggressive = aggressive, events = events))
    }

    data class FilingQuote(val legit: Double, val refund: Money, val aggressiveRefund: Money)

    fun quote(f: TaxFiling): FilingQuote {
        val legit = f.deductions.values.sum()
        return FilingQuote(legit, f.withheld * Multiplier.ofDouble(Credit.round6(legit)), f.withheld * Multiplier.ofDouble(Credit.round6(legit + f.aggressiveExtra)))
    }

    private fun file(s0: GameState, f: TaxFiling, selfFile: Boolean, aggressive: Boolean, events: MutableList<GameEvent>): GameState {
        var s = s0
        val legit = if (selfFile) f.deductions.values.sum() else 0.0
        val extra = if (selfFile && aggressive) f.aggressiveExtra else 0.0
        val audited = extra > 0 && GameRandom.stream(s.worldSeed, "audit", f.quarter).nextDouble() < f.auditChance
        val share = legit + extra - if (audited) extra * cfg.auditPenaltyMultiple else 0.0
        val net = f.withheld * Multiplier.ofDouble(Credit.round6(kotlin.math.abs(share)))
        if (share > 0 && net.isPositive) {
            s = post(s, TxnCategory.Tax, "tax refund (Q${f.quarter + 1})", listOf(Posting(Accounts.CASH, net), Posting(Accounts.revenue("tax_refund"), -net))).first!!
        } else if (share < 0 && net.isPositive) {
            // Audit penalty larger than the refund: pay from cash; what cash can't cover becomes a 1-day loan.
            val fromCash = net.min(s.cash.max(Money.ZERO))
            val rest = net - fromCash
            s = post(s, TxnCategory.Penalty, "audit penalty", listOf(Posting(Accounts.CASH, -fromCash), Posting(LOANS, -rest), Posting(Accounts.expense("tax_penalty"), net)), emptyMap()).first!!
            if (rest.isPositive) {
                val rate = credit.loanRatePerDay(s)
                val loan = LoanState(s.finance.nextLoanId, rest, credit.installment(rest, rate, 1), 4, rate, s.simTimeMs, rest)
                s = withFinance(s, s.finance.copy(loans = s.finance.loans + loan, nextLoanId = loan.id + 1))
            }
        }
        if (audited) s = adjustScore(s, cfg.auditPoints)
        val refund = if (share > 0) net else -net
        val text = when {
            !selfFile -> "Filed automatically: always correct, no savings, no risk."
            audited -> "The tax office audited your aggressive return. You repaid the difference plus a penalty."
            aggressive -> "Your aggressive return was accepted."
            else -> "You claimed the deductions you earned."
        }
        events += GameEvent.TaxFiled(refund, audited, !selfFile)
        val tax = s.finance.tax
        s = withFinance(s, s.finance.copy(
            audits = s.finance.audits + if (audited) 1 else 0,
            tax = tax.copy(pending = null, lastResult = text, totalRefunds = tax.totalRefunds + if (share > 0) net else Money.ZERO),
        ))
        return log(s, "Taxes filed (Q${f.quarter + 1})", text, if (audited) false else selfFile)
    }

    // =====================================================================================
    // Ticks
    // =====================================================================================

    /**
     * Minute tick: the 6-hourly settlement, tax quarters, the Phoenix clock, and credit reactions to [newEvents]
     * (foreclosures, missed mortgage payments) raised earlier in this boundary.
     */
    fun tick(state: GameState, minute: Long, newEvents: List<GameEvent>, events: MutableList<GameEvent>): GameState {
        var s = state
        for (e in newEvents) {
            when (e) {
                is GameEvent.Foreclosure -> s = adjustScore(s, cfg.foreclosurePoints)
                is GameEvent.PaymentMissed -> if (e.source == "mortgage") s = adjustScore(s, cfg.missedPoints).let { it.copy(finance = it.finance.copy(missedPayments = it.finance.missedPayments + 1)) }
                else -> Unit
            }
        }
        if (minute > 0 && minute % 360 == 0L) s = settle(s, events)
        s = taxes(s, minute, events)
        // The Phoenix clock only runs once there is something to rescue (debt).
        val negative = (s.finance.loans.isNotEmpty() || s.finance.creditLine.isPositive || s.estate.properties.values.any { it.mortgage.isPositive }) &&
            economy.netWorth(s).isNegative
        val since = s.finance.negativeSince
        if (negative && since < 0) s = withFinance(s, s.finance.copy(negativeSince = s.simTimeMs))
        if (!negative && since >= 0) s = withFinance(s, s.finance.copy(negativeSince = -1))
        return s
    }

    private fun settle(state: GameState, events: MutableList<GameEvent>): GameState {
        var s = state
        val f0 = s.finance
        if (f0.loans.isEmpty() && !f0.creditLine.isPositive) {
            return if (f0.scoreHistory < cfg.recoverTo) adjustScore(s, cfg.recoverPer6h) else s
        }
        val loans = mutableListOf<LoanState>()
        var onTime = 0L
        var missed = 0L
        for (l in f0.loans.sortedBy { it.id }) {
            val interest = l.principal * Multiplier.ofDouble(Credit.round6(l.ratePerDay / 4))
            val due = if (l.paymentsLeft <= 1) l.principal + interest else l.installment.max(interest)
            if (s.cash >= due) {
                val principalPart = (due - interest).min(l.principal)
                s = post(s, TxnCategory.Loan, "loan payment", listOf(Posting(Accounts.CASH, -due), Posting(LOANS, principalPart), Posting(Accounts.expense("interest"), due - principalPart))).first ?: s
                onTime++
                val left = l.principal - principalPart
                if (left.isPositive && l.paymentsLeft > 1) loans += l.copy(principal = left, paymentsLeft = l.paymentsLeft - 1)
                else events += GameEvent.LoanRepaid(l.id)
            } else {
                // Missed: interest and a penalty are added to the loan; the schedule slips by one payment.
                val penalty = l.principal * Multiplier.ofDouble(cfg.missedPenalty)
                val add = interest + penalty
                s = post(s, TxnCategory.Penalty, "missed loan payment", listOf(Posting(LOANS, -add), Posting(Accounts.expense("interest"), add)), emptyMap()).first!!
                missed++
                events += GameEvent.PaymentMissed("loan", due)
                loans += l.copy(principal = l.principal + add)
            }
        }
        var line = f0.creditLine
        if (line.isPositive) {
            val interest = line * Multiplier.ofDouble(Credit.round6(credit.loanRatePerDay(s) / 4))
            if (s.cash >= interest) {
                s = post(s, TxnCategory.Interest, "credit line interest", listOf(Posting(Accounts.CASH, -interest), Posting(Accounts.expense("interest"), interest))).first ?: s
            } else {
                s = post(s, TxnCategory.Interest, "credit line interest (added)", listOf(Posting(CREDIT_LINE, -interest), Posting(Accounts.expense("interest"), interest)), emptyMap()).first!!
                line += interest
                missed++
                events += GameEvent.PaymentMissed("credit_line", interest)
            }
        }
        s = withFinance(s, s.finance.copy(loans = loans, creditLine = line, onTimePayments = s.finance.onTimePayments + onTime, missedPayments = s.finance.missedPayments + missed))
        val points = onTime.toInt() * cfg.onTimePoints + missed.toInt() * cfg.missedPoints
        s = adjustScore(s, points)
        if (missed > 0) s = log(s, "Missed payment", "A loan payment was missed. The amount was added to the loan and your credit score fell.", false)
        return s
    }

    private fun taxes(state: GameState, minute: Long, events: MutableList<GameEvent>): GameState {
        var s = state
        val t = s.finance.tax
        if (!world.isUnlocked(s, "taxes")) return s
        if (t.startMinute < 0) {
            val rate = credit.effectiveRate(credit.hourlyIncome(s))
            return withFinance(s, s.finance.copy(tax = t.copy(startMinute = minute, quarter = 0, rate = rate,
                incomeAtStart = credit.businessIncomeTotal(s), withheldAtStart = credit.taxWithheldTotal(s))))
        }
        // Auto-file a return whose window has closed.
        t.pending?.let { p -> if (s.simTimeMs >= p.dueSim) s = file(s, p, selfFile = false, aggressive = false, events = events) }
        val qMinutes = cfg.quarterHours * 60L
        if (minute > t.startMinute && (minute - t.startMinute) % qMinutes == 0L) {
            val tt = s.finance.tax
            if (tt.pending != null) s = file(s, tt.pending, selfFile = false, aggressive = false, events = events)
            val income = credit.businessIncomeTotal(s) - tt.incomeAtStart
            val withheld = credit.taxWithheldTotal(s) - tt.withheldAtStart
            val r = GameRandom.stream(s.worldSeed, "tax_quarter", tt.quarter)
            val filing = TaxFiling(
                quarter = tt.quarter, income = income, withheld = withheld, deductions = credit.deductions(s, withheld),
                dueSim = s.simTimeMs + cfg.filingWindowHours * 3_600_000L,
                aggressiveExtra = Credit.round6(r.nextDouble(cfg.aggressiveExtraMin, cfg.aggressiveExtraMax)),
                auditChance = Credit.round6(r.nextDouble(cfg.auditChanceMin, cfg.auditChanceMax)),
            )
            val newRate = credit.effectiveRate(Money.ofCents(income.cents.divide(java.math.BigInteger.valueOf(cfg.quarterHours.toLong()))))
            events += GameEvent.QuarterEnded(tt.quarter)
            s = withFinance(s, s.finance.copy(tax = s.finance.tax.copy(
                quarter = tt.quarter + 1, rate = newRate, incomeAtStart = credit.businessIncomeTotal(s), withheldAtStart = credit.taxWithheldTotal(s),
                pending = if (withheld.isPositive) filing else null,
            )))
            s = s.copy(world = s.world.copy(donationsThisQuarter = Money.ZERO, rdThisQuarter = Money.ZERO))
            if (withheld.isPositive && s.finance.tax.autoFile) s = file(s, filing, selfFile = false, aggressive = false, events = events)
            else if (withheld.isPositive) s = log(s, "Tax return due", "Quarter ${tt.quarter + 1} has ended. File it yourself within ${cfg.filingWindowHours} hours to claim your deductions, or it is filed automatically.", null)
        }
        return s
    }

    companion object {
        const val LOANS = "liability:loans"
        const val CREDIT_LINE = "liability:credit_line"
    }
}
