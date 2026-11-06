package com.arnavgames.tycoon.core.ledger

import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.random.GameRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerTest {
    private fun ok(r: Ledger.Result) = (r as Ledger.Result.Ok).state

    @Test
    fun unbalancedTransactionsAreImpossible() {
        assertThrows(IllegalArgumentException::class.java) {
            Txn(1, 0, TxnCategory.Adjustment, "bad", listOf(Posting(Accounts.CASH, Money.ofCents(5)), Posting(Accounts.WORLD, Money.ofCents(-4))))
        }
    }

    @Test
    fun trialBalanceAlwaysZeroAndBalancesMatchReplay() {
        val rng = GameRandom.stream(42, "ledger-test")
        var s = LedgerState()
        val replay = mutableMapOf<String, Money>()
        var posted = 0
        repeat(100_000) { i ->
            val amount = Money.ofCents(rng.nextLong() % 1_000_000_000L).abs()
            when (rng.nextInt(3)) {
                0 -> {
                    val src = "biz${rng.nextInt(5)}"
                    s = Ledger.earn(s, i * 500L, src, amount)
                    replay.merge(Accounts.CASH, amount, Money::plus)
                    replay.merge(Accounts.revenue(src), -amount, Money::plus)
                }
                1 -> when (val r = Ledger.spend(s, i * 500L, TxnCategory.Purchase, "buy", "things", amount)) {
                    is Ledger.Result.Ok -> {
                        s = r.state; posted++
                        replay.merge(Accounts.CASH, -amount, Money::plus)
                        replay.merge(Accounts.expense("things"), amount, Money::plus)
                    }
                    is Ledger.Result.Err -> assertTrue(s.cash < amount)
                }
                else -> {
                    s = ok(Ledger.post(s, i * 500L, TxnCategory.Reward, "grant", listOf(Posting(Accounts.CASH, amount), Posting(Accounts.WORLD, -amount))))
                    replay.merge(Accounts.CASH, amount, Money::plus)
                    replay.merge(Accounts.WORLD, -amount, Money::plus)
                }
            }
            assertTrue(!s.cash.isNegative, "cash floor respected")
        }
        assertEquals(Money.ZERO, Ledger.trialBalance(s))
        replay.filterValues { !it.isZero }.forEach { (acc, bal) -> assertEquals(bal, s.balance(acc), acc) }
        assertTrue(s.recent.size <= Ledger.RECENT_LIMIT)
        s.recent.forEach { t -> assertEquals(Money.ZERO, t.postings.fold(Money.ZERO) { a, p -> a + p.amount }) }
        assertTrue(posted > 0)
    }

    @Test
    fun incomeIsMergedPerSourcePerMinute() {
        var s = LedgerState()
        repeat(60) { sec ->
            s = Ledger.earn(s, sec * 1000L, "retail", Money.ofCents(10))
            s = Ledger.earn(s, sec * 1000L, "restaurants", Money.ofCents(20))
        }
        assertEquals(2, s.recent.size, "two sources in one minute → two Txns")
        s = Ledger.earn(s, 60_000L, "retail", Money.ofCents(10))
        assertEquals(3, s.recent.size, "new minute → new Txn")
        assertEquals(Money.ofCents(610 + 1200), s.cash)
        assertEquals(Money.ofCents(-610), s.balance(Accounts.revenue("retail")))
    }

    @Test
    fun floorBlocksOverspendWithClearError() {
        var s = ok(Ledger.post(LedgerState(), 0, TxnCategory.Reward, "g", listOf(Posting(Accounts.CASH, Money.ofCents(100)), Posting(Accounts.WORLD, Money.ofCents(-100)))))
        val r = Ledger.spend(s, 1, TxnCategory.Purchase, "x", "k", Money.ofCents(150))
        val err = (r as Ledger.Result.Err).error as LedgerError.InsufficientFunds
        assertEquals(Money.ofCents(150), err.needed)
        assertEquals(Money.ofCents(100), err.available)
        // An allowed overdraft (credit line) works when the floor is lowered.
        s = ok(Ledger.spend(s, 2, TxnCategory.Purchase, "x", "k", Money.ofCents(150), cashFloor = Money.ofCents(-100)))
        assertEquals(Money.ofCents(-50), s.cash)
    }
}
