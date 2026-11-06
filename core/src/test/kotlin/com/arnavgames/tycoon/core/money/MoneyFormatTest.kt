package com.arnavgames.tycoon.core.money

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class MoneyFormatTest {
    private fun d(dollars: String) = MoneyFormat.parseDollars(dollars)

    @Test
    fun fullFormatGoldenTable() {
        val cases = mapOf(
            "0" to "$0.00", "0.01" to "$0.01", "0.5" to "$0.50", "12.3" to "$12.30", "999.99" to "$999.99",
            "1000" to "$1,000.00", "12345.67" to "$12,345.67", "999999.99" to "$999,999.99",
            "1234567.89" to "$1,234,567.89", "-42.5" to "−$42.50",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, MoneyFormat.full(d(input)), input) }
    }

    @Test
    fun shortFormatGoldenTable() {
        val cases = mapOf(
            "999999.99" to "$999,999.99",
            "1000000" to "$1.00M", "1234567" to "$1.23M", "1239999" to "$1.23M", // truncates, never rounds up
            "12345678" to "$12.3M", "123456789" to "$123M", "999999999" to "$999M",
            "1000000000" to "$1.00B", "45600000000" to "$45.6B", "789000000000000" to "$789T",
            "-2500000" to "−$2.50M",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, MoneyFormat.short(d(input)), input) }
    }

    @Test
    fun everySuffixInOrder() {
        val expected = listOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc", "aa", "ab")
        expected.forEachIndexed { g, s -> assertEquals(s, MoneyFormat.suffix(g)) }
        assertEquals("az", MoneyFormat.suffix(12 + 25))
        assertEquals("ba", MoneyFormat.suffix(12 + 26))
        assertEquals("zz", MoneyFormat.suffix(12 + 26 * 26 - 1))
        // 10^36 dollars = group 12 → "aa"
        val big = Money.ofCents(BigInteger.TEN.pow(38))
        assertEquals("$1.00aa", MoneyFormat.short(big))
        assertEquals("$1.00Dc", MoneyFormat.short(Money.ofCents(BigInteger.TEN.pow(35))))
    }

    @Test
    fun compactAndScientific() {
        assertEquals("$999.99", MoneyFormat.compact(d("999.99")))
        assertEquals("$12.3K", MoneyFormat.compact(d("12345")))
        assertEquals("$1.23e45", MoneyFormat.short(Money.ofCents(BigInteger("123456").multiply(BigInteger.TEN.pow(42))), MoneyFormat.Notation.Scientific))
        assertEquals("$12,345.00", MoneyFormat.short(d("12345"), MoneyFormat.Notation.Scientific))
        assertEquals("+$1.50M", MoneyFormat.signedShort(d("1500000")))
        assertEquals("$12.3K/s", MoneyFormat.perSecond(d("12345")))
    }

    @Test
    fun fullFormatRoundTripsThroughParse(): Unit = runBlocking {
        checkAll(3000, Arb.long()) { c ->
            val m = Money.ofCents(c)
            assertEquals(m, MoneyFormat.parseDollars(MoneyFormat.full(m)))
        }
    }
}
