package com.arnavgames.tycoon.core.finance

import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import com.arnavgames.tycoon.core.engine.EngineFixtures.engine
import com.arnavgames.tycoon.core.engine.EngineFixtures.newGame
import com.arnavgames.tycoon.core.engine.EngineFixtures.reject
import com.arnavgames.tycoon.core.engine.EngineFixtures.run
import com.arnavgames.tycoon.core.engine.EngineFixtures.withHugeCash
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.LoanState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinanceTest {
    private val hour = 3_600_000L
    private val credit = engine.credit
    private val cfg = engine.content.world.config.finance

    private fun trial(s: GameState) = assertTrue(Ledger.trialBalance(s.ledger).isZero, "ledger out of balance")
    private fun at(s: GameState, ch: Int) = s.copy(progress = s.progress.copy(chapter = ch, onboardingStep = 5))
    private fun drain(s: GameState, keep: Money = Money.ZERO): GameState {
        val amt = (s.cash - keep).max(Money.ZERO)
        return s.copy(ledger = (Ledger.spend(s.ledger, s.simTimeMs, TxnCategory.Purchase, "drain", "test", amt) as Ledger.Result.Ok).state)
    }

    @Test
    fun aNewPlayerIsRatedBbb() {
        val s = newGame(1)
        assertEquals(700, credit.score(s))
        assertEquals("BBB", credit.rating(s).name)
        assertEquals(listOf(0.0, 0.25, 0.5, 0.6), engine.estate.ltvOptions(s))
    }

    @Test
    fun loansAreRepaidEverySixHoursAndBuildCredit() {
        var s = at(busyState(), 3)
        val limit = credit.borrowLimit(s)
        assertTrue(limit.isPositive)
        val amount = limit * Multiplier.of("0.5")
        s = s.run(Command.TakeLoan(amount, 1))
        assertEquals(amount, credit.loanDebt(s))
        assertEquals(-amount, s.ledger.balance(FinanceEngine.LOANS))
        s = s.adv(24 * hour + 60_000)
        assertTrue(s.finance.loans.isEmpty(), "repaid after four payments")
        assertEquals(4, s.finance.onTimePayments)
        assertEquals(Money.ZERO, s.ledger.balance(FinanceEngine.LOANS))
        assertTrue(s.finance.scoreHistory >= 700 + 4 * cfg.onTimePoints)
        trial(s)
    }

    @Test
    fun borrowingIsCappedAndNeedsChapterThree() {
        val early = at(busyState(), 2)
        assertInstanceOf(GameError.Locked::class.java, early.reject(Command.TakeLoan(Money.ofDollars(100), 1)))
        val s = at(busyState(), 3)
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.TakeLoan(credit.borrowLimit(s) + Money.ofDollars(1), 1)))
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.TakeLoan(Money.ofDollars(100), 2)), "unsupported term")
    }

    @Test
    fun aMissedPaymentGrowsTheLoanAndCutsTheScore() {
        var s = at(busyState(), 3)
        val amount = credit.borrowLimit(s) * Multiplier.of("0.5")
        s = s.run(Command.TakeLoan(amount, 7))
        // Spend everything and stop all income so the next payment cannot be made.
        s = drain(s).copy(businesses = s.businesses.mapValues { it.value.copy(mothballed = true) })
        val before = s.finance.loans.single().principal
        s = s.adv(6 * hour + 60_000)
        assertEquals(1, s.finance.missedPayments)
        assertTrue(s.finance.loans.single().principal > before)
        assertEquals(700 + cfg.missedPoints, s.finance.scoreHistory)
        trial(s)
    }

    @Test
    fun creditLineDrawsAndRepays() {
        var s = at(busyState(), 3)
        val lim = credit.creditLineLimit(s)
        s = s.run(Command.DrawCredit(lim * Multiplier.of("0.5")))
        assertEquals(lim * Multiplier.of("0.5"), s.finance.creditLine)
        assertInstanceOf(GameError.Invalid::class.java, s.reject(Command.DrawCredit(lim)))
        s = s.run(Command.RepayCredit(s.finance.creditLine))
        assertEquals(Money.ZERO, s.finance.creditLine)
        trial(s)
    }

    @Test
    fun incomeTaxIsWithheldAtTheQuarterRate() {
        var s = at(busyState(), 4).adv(60_000)
        val t = s.finance.tax
        assertTrue(t.startMinute >= 0 && t.rate in 0.15..0.35, "rate ${t.rate}")
        val income0 = credit.businessIncomeTotal(s)
        val tax0 = credit.taxWithheldTotal(s)
        s = s.adv(hour)
        val income = credit.businessIncomeTotal(s) - income0
        val tax = credit.taxWithheldTotal(s) - tax0
        val expected = income.toDollarsDouble() * t.rate
        assertEquals(expected, tax.toDollarsDouble(), expected * 1e-6 + 1.0)
        // The Hustle is never taxed (never-stuck floor).
        val tapped = s.run(Command.HustleTap(1))
        assertEquals(credit.taxWithheldTotal(s), credit.taxWithheldTotal(tapped))
        trial(s)
    }

    @Test
    fun quartersEndWithAReturnThatIsFiledAutomaticallyIfIgnored() {
        var s = at(busyState(), 4).adv(60_000)
        s = s.adv(cfg.quarterHours * hour)
        val f = s.finance.tax.pending
        assertNotNull(f)
        assertTrue(f!!.withheld.isPositive)
        assertEquals(1, s.finance.tax.quarter)
        s = s.adv(cfg.filingWindowHours * hour + 60_000)
        assertNull(s.finance.tax.pending)
        assertTrue(s.finance.tax.lastResult!!.startsWith("Filed automatically"))
        assertEquals(Money.ZERO, s.ledger.balance(Accounts.revenue("tax_refund")))
    }

    @Test
    fun selfFilingRefundsTheDeductionsYouEarned() {
        var s = at(busyState(), 4).adv(60_000)
        // Earn deductions: own an energy business (green credit) and a property (depreciation).
        s = s.withHugeCash().run(Command.OpenBusiness("energy"))
        val listing = engine.content.estates.districts.filter { it.city == "lisbon" }.flatMap { engine.estate.listings(s, it.id) }.first()
        s = s.run(Command.BuyProperty(listing.id))
        s = s.adv(cfg.quarterHours * hour)
        val f = s.finance.tax.pending!!
        assertEquals(setOf("depreciation", "green"), f.deductions.keys)
        val quote = engine.finance.quote(f)
        val cash = s.cash
        s = s.run(Command.FileTaxes(aggressive = false))
        assertEquals(cash + quote.refund, s.cash)
        assertEquals(quote.refund, -s.ledger.balance(Accounts.revenue("tax_refund")))
        trial(s)
    }

    @Test
    fun aggressiveFilingIsSometimesAudited() {
        var audited = 0
        var accepted = 0
        for (seed in 1L..24L) {
            var s = at(busyState().copy(worldSeed = seed), 4).adv(60_000)
            s = s.adv(cfg.quarterHours * hour)
            val f = s.finance.tax.pending!!
            assertTrue(f.auditChance in cfg.auditChanceMin..cfg.auditChanceMax)
            val q = engine.finance.quote(f)
            val cash = s.cash
            s = s.run(Command.FileTaxes(aggressive = true))
            if (s.finance.audits == 1) {
                audited++
                assertTrue(s.cash < cash + q.aggressiveRefund)
                assertEquals(700 + cfg.auditPoints, s.finance.scoreHistory)
            } else {
                accepted++
                assertEquals(cash + q.aggressiveRefund, s.cash)
            }
            trial(s)
        }
        assertTrue(audited in 1..20 && accepted in 4..23, "audited $audited, accepted $accepted")
    }

    @Test
    fun restructuringStretchesLoansWhenPaymentsExceedIncome() {
        var s = at(busyState(), 3)
        val big = credit.hourlyIncome(s) * 100L
        val loan = LoanState(1, big, big * Multiplier.of("0.3"), 4, 0.01, s.simTimeMs, big)
        s = s.copy(
            ledger = (Ledger.post(s.ledger, s.simTimeMs, TxnCategory.Loan, "test loan", listOf(Posting(Accounts.CASH, big), Posting(FinanceEngine.LOANS, -big))) as Ledger.Result.Ok).state,
            finance = s.finance.copy(loans = listOf(loan), nextLoanId = 2),
        )
        assertTrue(credit.canRestructure(s))
        val r = s.run(Command.Restructure)
        assertEquals(8, r.finance.loans.single().paymentsLeft)
        assertTrue(r.finance.loans.single().installment < loan.installment)
        assertEquals(700 + cfg.restructurePoints, r.finance.scoreHistory)
        assertInstanceOf(GameError.Locked::class.java, at(busyState(), 3).reject(Command.Restructure))
    }

    @Test
    fun phoenixProtocolClearsDebtsAndKeepsTheBusinesses() {
        var s = at(busyState(), 3)
        // Huge debt with nothing to show for it: net worth far below zero.
        val big = engine.economy.netWorth(s) * 10L
        s = s.copy(
            ledger = (Ledger.post(s.ledger, s.simTimeMs, TxnCategory.Loan, "test debt", listOf(Posting(FinanceEngine.LOANS, -big), Posting(Accounts.expense("test"), big))) as Ledger.Result.Ok).state,
            finance = s.finance.copy(loans = listOf(LoanState(1, big, big, 28, 0.01, s.simTimeMs, big)), nextLoanId = 2),
        )
        assertTrue(engine.economy.netWorth(s).isNegative)
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.AcceptPhoenix))
        s = s.copy(businesses = s.businesses.mapValues { it.value.copy(mothballed = true) }).adv(31 * 60_000)
        assertTrue(credit.phoenixAvailable(s))
        val levels = s.businesses.mapValues { it.value.level }
        val r = s.run(Command.AcceptPhoenix)
        assertTrue(r.finance.loans.isEmpty())
        assertEquals(Money.ZERO, r.ledger.balance(FinanceEngine.LOANS))
        assertTrue(engine.economy.netWorth(r) >= Money.ZERO)
        levels.forEach { (id, lv) -> assertEquals(maxOf(1, (lv * cfg.phoenixKeepLevels).toInt()), r.businesses.getValue(id).level) }
        assertTrue(r.world.modifiers.any { it.source == "phoenix" && it.pct == cfg.phoenixBoost - 1.0 })
        assertEquals(cfg.phoenixScore, r.finance.scoreHistory)
        trial(r)
    }

    @Test
    fun theCreditRatingCapsMortgageLoanToValue() {
        var s = at(newGame(4).withHugeCash(), 3)
        s = s.copy(finance = s.finance.copy(scoreHistory = 560)) // CCC
        assertEquals("CCC", credit.rating(s).name)
        assertEquals(listOf(0.0, 0.25), engine.estate.ltvOptions(s))
        val listing = engine.content.estates.districts.filter { it.city == "lisbon" }.flatMap { engine.estate.listings(s, it.id) }.first()
        assertInstanceOf(GameError.Locked::class.java, s.reject(Command.BuyProperty(listing.id, ltv = 0.5)))
        s.run(Command.BuyProperty(listing.id, ltv = 0.25))
    }

    @Test
    fun taxBracketsAreProgressive() {
        assertEquals(0.15, credit.effectiveRate(Money.ofDollars(1_000_000)), 1e-9)
        val mid = credit.effectiveRate(Money.ofDollars(1_000_000_000_000L * 10))
        assertTrue(mid in 0.22..0.28, "effective $mid")
        assertEquals(0.28, credit.marginalRate(Money.ofDollars(1_000_000_000_000L * 10)), 1e-9)
    }
}
