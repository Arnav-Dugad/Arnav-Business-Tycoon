package com.arnavgames.tycoon.core.ledger

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.Serializable

/**
 * Double-entry ledger: the ONLY way any balance changes (docs/ARCHITECTURE.md §3.2).
 *
 * Sign convention: a positive posting is a debit. Asset accounts (Cash, Bullion…) carry positive balances;
 * revenue accounts accumulate negative (credit) balances; expense accounts accumulate positive balances.
 * Every transaction sums to zero, so the sum of all balances is always zero (property-tested).
 * Revenue and expense balances double as lifetime statistics.
 */
@Serializable
data class LedgerState(
    val balances: Map<String, Money> = emptyMap(),
    val nextTxnId: Long = 1,
    /** Most recent transactions for UI history (older ones are persisted by the app's journal table). */
    val recent: List<Txn> = emptyList(),
) {
    fun balance(account: String): Money = balances[account] ?: Money.ZERO

    val cash: Money get() = balance(Accounts.CASH)
    val bullion: Money get() = balance(Accounts.BULLION)
    val favor: Money get() = balance(Accounts.FAVOR)
}

@Serializable
data class Posting(val account: String, val amount: Money)

@Serializable
data class Txn(
    val id: Long,
    /** Game time (sim ms) at which the transaction was posted. */
    val time: Long,
    val category: TxnCategory,
    val memo: String,
    val postings: List<Posting>,
) {
    init {
        require(postings.size >= 2) { "a transaction needs at least two postings" }
        require(postings.fold(Money.ZERO) { a, p -> a + p.amount }.isZero) { "unbalanced transaction: $postings" }
    }
}

@Serializable
enum class TxnCategory {
    Income, Purchase, Upgrade, Hire, Expansion, Tax, Interest, Loan, Investment, Property, Luxury,
    Reward, PremiumGrant, PremiumSpend, Gift, Penalty, Refund, Adjustment, Prestige,
}

object Accounts {
    const val CASH = "asset:cash"
    const val BULLION = "asset:bullion"
    const val FAVOR = "asset:favor"
    const val WORLD = "world"                 // external source/sink for cash flows
    const val WORLD_PREMIUM = "world:premium" // source/sink for Bullion and Favor

    fun revenue(source: String) = "revenue:$source"
    fun expense(kind: String) = "expense:$kind"
}

sealed interface LedgerError {
    data class InsufficientFunds(val account: String, val needed: Money, val available: Money) : LedgerError
}

object Ledger {
    /** How many transactions are kept in [LedgerState.recent]. */
    const val RECENT_LIMIT = 200

    /**
     * Posts a balanced transaction. Asset accounts listed in [floors] may not end below their floor
     * (e.g. Cash may not go below −creditLine). Returns the new state + the posted Txn, or an error.
     */
    fun post(
        state: LedgerState,
        time: Long,
        category: TxnCategory,
        memo: String,
        postings: List<Posting>,
        floors: Map<String, Money> = DEFAULT_FLOORS,
    ): Result {
        val merged = postings.groupBy { it.account }
            .map { (acc, ps) -> Posting(acc, ps.fold(Money.ZERO) { a, p -> a + p.amount }) }
            .filterNot { it.amount.isZero }
        if (merged.isEmpty()) return Result.Ok(state, null)
        val txn = Txn(state.nextTxnId, time, category, memo, merged) // validates balance
        val newBalances = state.balances.toMutableMap()
        for (p in merged) newBalances[p.account] = (newBalances[p.account] ?: Money.ZERO) + p.amount
        for ((acc, floor) in floors) {
            val after = newBalances[acc] ?: continue
            val touched = merged.any { it.account == acc && it.amount.isNegative }
            if (touched && after < floor) {
                val before = state.balance(acc)
                val needed = -(merged.first { it.account == acc }.amount)
                return Result.Err(LedgerError.InsufficientFunds(acc, needed = needed, available = (before - floor).max(Money.ZERO)))
            }
        }
        val recent = (state.recent + txn).let { if (it.size > RECENT_LIMIT) it.takeLast(RECENT_LIMIT) else it }
        return Result.Ok(LedgerState(newBalances, state.nextTxnId + 1, recent), txn)
    }

    /** Pays [amount] from Cash to an expense account. */
    fun spend(state: LedgerState, time: Long, category: TxnCategory, memo: String, expenseKind: String, amount: Money, cashFloor: Money = Money.ZERO): Result {
        require(!amount.isNegative)
        return post(
            state, time, category, memo,
            listOf(Posting(Accounts.CASH, -amount), Posting(Accounts.expense(expenseKind), amount)),
            floors = mapOf(Accounts.CASH to cashFloor, Accounts.BULLION to Money.ZERO, Accounts.FAVOR to Money.ZERO),
        )
    }

    /**
     * Records continuous income. To keep the journal compact, income from the same source within the same
     * game minute is merged into one transaction (the open minute's Txn is replaced, balances stay exact).
     */
    fun earn(state: LedgerState, time: Long, source: String, amount: Money, tax: Money = Money.ZERO): LedgerState {
        require(!amount.isNegative && !tax.isNegative && tax <= amount)
        if (amount.isZero) return state
        val revenueAcc = Accounts.revenue(source)
        val taxAcc = Accounts.expense(INCOME_TAX)
        val minute = time / 60_000
        val newBalances = state.balances.toMutableMap()
        newBalances[Accounts.CASH] = state.cash + (amount - tax)
        newBalances[revenueAcc] = state.balance(revenueAcc) - amount
        if (tax.isPositive) newBalances[taxAcc] = state.balance(taxAcc) + tax
        // Find this source's open income Txn within the current minute (scan back only through this minute).
        var idx = -1
        for (i in state.recent.indices.reversed()) {
            val t = state.recent[i]
            if (t.time / 60_000 != minute) break
            if (t.category == TxnCategory.Income && t.memo == source) { idx = i; break }
        }
        fun postings(gross: Money, withheld: Money): List<Posting> =
            if (withheld.isPositive) listOf(Posting(Accounts.CASH, gross - withheld), Posting(revenueAcc, -gross), Posting(taxAcc, withheld))
            else listOf(Posting(Accounts.CASH, gross), Posting(revenueAcc, -gross))
        if (idx >= 0) {
            val open = state.recent[idx]
            val prevGross = -(open.postings.first { it.account == revenueAcc }.amount)
            val prevTax = open.postings.firstOrNull { it.account == taxAcc }?.amount ?: Money.ZERO
            val updated = open.copy(postings = postings(prevGross + amount, prevTax + tax))
            val recent = state.recent.toMutableList().also { it[idx] = updated }
            return state.copy(balances = newBalances, recent = recent)
        }
        // Income Txns are stamped with the minute start so split and unsplit advances produce identical journals.
        val txn = Txn(state.nextTxnId, minute * 60_000, TxnCategory.Income, source, postings(amount, tax))
        val recent = (state.recent + txn).let { if (it.size > RECENT_LIMIT) it.takeLast(RECENT_LIMIT) else it }
        return LedgerState(newBalances, state.nextTxnId + 1, recent)
    }

    /** Expense account that collects income tax withheld from business income (Phase 9). */
    const val INCOME_TAX = "income_tax"

    /** Sum of every balance; must always be zero. */
    fun trialBalance(state: LedgerState): Money = state.balances.values.fold(Money.ZERO) { a, b -> a + b }

    val DEFAULT_FLOORS = mapOf(Accounts.CASH to Money.ZERO, Accounts.BULLION to Money.ZERO, Accounts.FAVOR to Money.ZERO)

    sealed interface Result {
        data class Ok(val state: LedgerState, val txn: Txn?) : Result
        data class Err(val error: LedgerError) : Result
    }
}
