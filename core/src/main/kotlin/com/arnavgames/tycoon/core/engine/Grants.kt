package com.arnavgames.tycoon.core.engine

import com.arnavgames.tycoon.core.ledger.Accounts
import com.arnavgames.tycoon.core.ledger.Ledger
import com.arnavgames.tycoon.core.ledger.LedgerState
import com.arnavgames.tycoon.core.ledger.Posting
import com.arnavgames.tycoon.core.ledger.TxnCategory
import com.arnavgames.tycoon.core.money.Money

/** Rewards from outside the economy (missions, daily rewards, events), always posted through the Ledger. */
object Grants {
    fun grant(ledger: LedgerState, time: Long, memo: String, cash: Money, bullion: Long, favor: Long): LedgerState {
        val postings = buildList {
            if (cash.isPositive) { add(Posting(Accounts.CASH, cash)); add(Posting(Accounts.WORLD, -cash)) }
            if (bullion > 0) { add(Posting(Accounts.BULLION, Money.ofCents(bullion))); add(Posting(Accounts.WORLD_PREMIUM, Money.ofCents(-bullion))) }
            if (favor > 0) { add(Posting(Accounts.FAVOR, Money.ofCents(favor))); add(Posting(Accounts.WORLD_PREMIUM, Money.ofCents(-favor))) }
        }
        if (postings.isEmpty()) return ledger
        return (Ledger.post(ledger, time, TxnCategory.Reward, memo, postings) as Ledger.Result.Ok).state
    }
}
