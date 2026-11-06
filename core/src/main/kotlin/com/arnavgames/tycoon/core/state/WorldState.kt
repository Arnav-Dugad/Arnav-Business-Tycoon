package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.Serializable

/**
 * World events, decision cards, rivals and story beats (docs/GDD.md §9–10). Rival net worths are pure functions of
 * time and the world seed, so only relationships and what the player did are stored.
 */
@Serializable
data class WorldState(
    /** Decision cards waiting for the player (at most 3; never resolved automatically). */
    val cards: List<CardState> = emptyList(),
    val nextCardId: Long = 1,
    /** Sim time of the next card draw (0 = not scheduled yet). */
    val nextCardAt: Long = 0,
    val cardDraws: Long = 0,
    /** Recently drawn card ids (most recent last), so cards don't repeat too soon. */
    val recentCards: List<String> = emptyList(),
    /** Timed income modifiers from events, cards and the Phoenix comeback boost. */
    val modifiers: List<ModifierState> = emptyList(),
    /** Sim time of the next world event (0 = not scheduled yet). */
    val nextEventAt: Long = 0,
    val eventDraws: Long = 0,
    /** Rival id -> relationship (-100..100). Missing = 0. */
    val relations: Map<String, Int> = emptyMap(),
    /** Chapters whose nemesis beats have been shown: "<chapter>:<beat>" (beat 0 intro, 1 midpoint, 2 finale). */
    val beats: Set<String> = emptySet(),
    /** Results of resolved cards and other world news for the Desk (newest last, at most 30). */
    val log: List<DeskEntry> = emptyList(),
    /** Businesses sold to rivals (for stats and the Desk). */
    val businessesSold: Int = 0,
    /** Tax deduction pools earned this quarter (from cards). */
    val donationsThisQuarter: Money = Money.ZERO,
    val rdThisQuarter: Money = Money.ZERO,
)

@Serializable
data class CardState(
    val id: Long,
    val card: String,
    val business: String? = null,
    val rival: String? = null,
    val createdSim: Long,
)

/** target: "all", "business:<id>" or "sector:<id>". pct is the income change (0.15 = +15 %). */
@Serializable
data class ModifierState(val source: String, val target: String, val pct: Double, val untilSim: Long, val title: String = "")

@Serializable
data class DeskEntry(val time: Long, val title: String, val text: String, val good: Boolean? = null)

/** Credit, loans, taxes and the never-stuck guarantees (docs/GDD.md §11). */
@Serializable
data class FinanceState(
    /** Payment-history part of the credit score; the shown score also includes a debt-to-income adjustment. */
    val scoreHistory: Int = 700,
    val loans: List<LoanState> = emptyList(),
    val nextLoanId: Long = 1,
    /** Amount drawn on the credit line. */
    val creditLine: Money = Money.ZERO,
    val onTimePayments: Long = 0,
    val missedPayments: Long = 0,
    val restructures: Int = 0,
    val audits: Int = 0,
    val tax: TaxState = TaxState(),
    /** Sim time since net worth has been below zero (-1 = not negative). */
    val negativeSince: Long = -1,
    val phoenixUsed: Int = 0,
)

@Serializable
data class LoanState(
    val id: Long,
    val principal: Money,
    /** Payment due every 6 hours (principal + interest). */
    val installment: Money,
    val paymentsLeft: Int,
    /** Interest per day, fixed when the loan was taken. */
    val ratePerDay: Double,
    val takenSim: Long,
    val original: Money,
)

@Serializable
data class TaxState(
    /** Minute the first quarter started (-1 = taxes not started yet). */
    val startMinute: Long = -1,
    val quarter: Long = 0,
    /** Withholding rate for the current quarter (set at each quarter start from the bracket schedule). */
    val rate: Double = 0.0,
    /** Ledger totals at the quarter start (business income and tax withheld), to measure the quarter. */
    val incomeAtStart: Money = Money.ZERO,
    val withheldAtStart: Money = Money.ZERO,
    /** A finished quarter waiting to be filed (auto-filed when the window closes). */
    val pending: TaxFiling? = null,
    /** File automatically at every quarter end (no savings, no risk). */
    val autoFile: Boolean = false,
    val lastResult: String? = null,
    val totalRefunds: Money = Money.ZERO,
)

@Serializable
data class TaxFiling(
    val quarter: Long,
    val income: Money,
    val withheld: Money,
    /** Deductions earned, each as a share of the tax withheld (rd, depreciation, donations, green). */
    val deductions: Map<String, Double>,
    val dueSim: Long,
    /** Extra share an aggressive interpretation would claim, and its audit chance (both shown up front). */
    val aggressiveExtra: Double,
    val auditChance: Double,
)
