package com.arnavgames.tycoon.core.money

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Formats [Money] exactly, working on the digit string so enormous values never pass through Double.
 *
 * - [full]: "$1,234,567.89" (grouping, always cents)
 * - [short]: full below $1M, then 3 significant digits + suffix: "$1.23M", "$45.6B", "$789T", "$1.00Qa"…"$1.00Dc",
 *   then two-letter suffixes "aa"…"zz". Digits are truncated (never rounded up) so values are never overstated.
 * - [compact]: like short but also abbreviates thousands ("$12.3K") for tight spaces.
 * - Scientific notation ("$1.23e45") when [Notation.Scientific] is selected in settings.
 * Negative values use the true minus sign "−".
 */
object MoneyFormat {
    enum class Notation { Suffix, Scientific }

    private const val MINUS = "−"
    private val NAMED = listOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")

    /** Suffix for 10^(3*group). group 0 = none, 1 = K, 2 = M ... 11 = Dc, 12 = aa, 13 = ab ... */
    fun suffix(group: Int): String {
        require(group >= 0)
        if (group < NAMED.size) return NAMED[group]
        val i = group - NAMED.size
        require(i < 26 * 26) { "value too large to name" }
        return "${'a' + i / 26}${'a' + i % 26}"
    }

    fun full(m: Money): String {
        val digits = m.cents.abs().toString().padStart(3, '0')
        val intPart = digits.substring(0, digits.length - 2)
        val frac = digits.substring(digits.length - 2)
        return sign(m) + "$" + group3(intPart) + "." + frac
    }

    /** Whole dollars with grouping, no cents (e.g. prices of large items). Truncates toward zero. */
    fun wholeDollars(m: Money): String {
        val digits = m.cents.abs().toString().padStart(3, '0')
        return sign(m) + "$" + group3(digits.substring(0, digits.length - 2))
    }

    fun short(m: Money, notation: Notation = Notation.Suffix): String = abbreviate(m, minGroup = 2, notation)

    fun compact(m: Money, notation: Notation = Notation.Suffix): String = abbreviate(m, minGroup = 1, notation)

    private fun abbreviate(m: Money, minGroup: Int, notation: Notation): String {
        val absCents = m.cents.abs()
        val dollarDigits = absCents.toString().let { if (it.length <= 2) "0" else it.substring(0, it.length - 2) }
        val nDigits = if (dollarDigits == "0") 0 else dollarDigits.length
        val group = if (nDigits == 0) 0 else (nDigits - 1) / 3
        if (group < minGroup) {
            // Below the abbreviation threshold: K-style compact shows whole dollars under $1,000 with cents.
            return full(m)
        }
        if (notation == Notation.Scientific && nDigits > 6) {
            val mant = dollarDigits[0] + "." + dollarDigits.substring(1, 3)
            return sign(m) + "$" + mant + "e" + (nDigits - 1)
        }
        val lead = nDigits - group * 3 // 1..3 digits before the decimal point
        val sig = dollarDigits.substring(0, 3)
        val body = if (lead == 3) sig else sig.substring(0, lead) + "." + sig.substring(lead)
        return sign(m) + "$" + body + suffix(group)
    }

    /** Signed change: "+$1.23M" / "−$45.00". */
    fun signedShort(m: Money, notation: Notation = Notation.Suffix): String =
        (if (m.isNegative) "" else "+") + short(m, notation)

    /** Rate per second, e.g. "$12.5K/s". */
    fun perSecond(m: Money, notation: Notation = Notation.Suffix): String = compact(m, notation) + "/s"

    /** Parses human input like "1,234.56", "$12", "-3.5". Rounds half-even to cents. */
    fun parseDollars(text: String): Money {
        val cleaned = text.trim().replace(MINUS, "-").replace("$", "").replace(",", "").replace(" ", "")
        require(cleaned.isNotEmpty()) { "empty amount" }
        return Money.ofDollars(BigDecimal(cleaned))
    }

    private fun sign(m: Money) = if (m.isNegative) MINUS else ""

    private fun group3(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val sb = StringBuilder(intPart.length + intPart.length / 3)
        val first = intPart.length % 3
        if (first > 0) sb.append(intPart, 0, first)
        var i = first
        while (i < intPart.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(intPart, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /** Exposed for tests: number of whole-dollar digits. */
    internal fun dollarDigitCount(cents: BigInteger): Int {
        val s = cents.abs().toString()
        return if (s.length <= 2) 0 else s.length - 2
    }
}
