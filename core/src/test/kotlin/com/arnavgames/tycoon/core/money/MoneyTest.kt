package com.arnavgames.tycoon.core.money

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger

class MoneyTest {
    /** Mix of small, boundary and huge values (up to ~1e300 cents). */
    private val anyMoney = Arb.choice(
        Arb.long().map { Money.ofCents(it) },
        Arb.long(Long.MAX_VALUE - 1000, Long.MAX_VALUE).map { Money.ofCents(it) },
        Arb.long(Long.MIN_VALUE, Long.MIN_VALUE + 1000).map { Money.ofCents(it) },
        Arb.bigInt(1, 1000).map { Money.ofCents(it) },
    )

    @Test
    fun additionMatchesBigIntegerExactly(): Unit = runBlocking {
        checkAll(5000, anyMoney, anyMoney) { a, b ->
            assertEquals(a.cents.add(b.cents), (a + b).cents)
            assertEquals(a.cents.subtract(b.cents), (a - b).cents)
        }
    }

    @Test
    fun algebraicLaws(): Unit = runBlocking {
        checkAll(3000, anyMoney, anyMoney, anyMoney) { a, b, c ->
            assertEquals(a + b, b + a)
            assertEquals((a + b) + c, a + (b + c))
            assertEquals(a, a + b - b)
            assertEquals(Money.ZERO, a - a)
            assertEquals(-(-a), a)
        }
    }

    @Test
    fun multiplicationByLongIsExact(): Unit = runBlocking {
        checkAll(3000, anyMoney, Arb.long(-1_000_000_000L, 1_000_000_000L)) { a, k ->
            assertEquals(a.cents.multiply(BigInteger.valueOf(k)), (a * k).cents)
        }
    }

    @Test
    fun longBigIntegerPromotionIsSeamless() {
        val max = Money.ofCents(Long.MAX_VALUE)
        val promoted = max + Money.ONE_CENT
        assertTrue(promoted.isBig)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), promoted.cents)
        val back = promoted - Money.ONE_CENT
        assertFalse(back.isBig, "values that fit a Long are demoted again")
        assertEquals(max, back)
        assertEquals(Money.ofCents(Long.MIN_VALUE).cents.negate(), (-Money.ofCents(Long.MIN_VALUE)).cents)
        assertTrue((Money.ofCents(Long.MAX_VALUE) * 3L).isBig)
    }

    @Test
    fun noOverflowUpToHugeValues() {
        var m = Money.ofDollars(1)
        repeat(1000) { m *= 2L } // 2^1000 dollars ≈ 1e301
        assertEquals(BigInteger.TWO.pow(1000).multiply(BigInteger.valueOf(100)), m.cents)
        assertTrue(m.log10Dollars() in 300.9..301.1)
    }

    @Test
    fun multiplierRoundsHalfEvenWithinHalfCent(): Unit = runBlocking {
        checkAll(3000, Arb.long(-1_000_000_000_000L, 1_000_000_000_000L), Arb.int(1, 100_000)) { cents, per ->
            val m = Multiplier.of(java.math.BigDecimal.valueOf(per.toLong(), 3)) // 0.001 .. 100.000
            val exact = java.math.BigDecimal(cents).multiply(m.value)
            val rounded = java.math.BigDecimal((Money.ofCents(cents) * m).cents)
            assertTrue(exact.subtract(rounded).abs() <= java.math.BigDecimal("0.5"))
        }
        assertEquals(Money.ofCents(2), Money.ofCents(5) * Multiplier.of("0.5"))  // 2.5 → 2 (half-even)
        assertEquals(Money.ofCents(4), Money.ofCents(7) * Multiplier.of("0.5"))  // 3.5 → 4
    }

    @Test
    fun comparisonAndEquality(): Unit = runBlocking {
        checkAll(3000, anyMoney, anyMoney) { a, b ->
            assertEquals(a.cents.compareTo(b.cents), a.compareTo(b).coerceIn(-1, 1))
            assertEquals(a.cents == b.cents, a == b)
            if (a == b) assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Test
    fun serializesAsExactCentsString(): Unit = runBlocking {
        checkAll(2000, anyMoney) { a ->
            val text = Json.encodeToString(MoneySerializer, a)
            assertEquals("\"${a.cents}\"", text)
            assertEquals(a, Json.decodeFromString(MoneySerializer, text))
        }
    }

    @Test
    fun divisionAndHelpers() {
        assertEquals(Money.ofCents(333), Money.ofCents(1000) / 3)
        assertEquals(Money.ofCents(-333), Money.ofCents(-1000) / 3)
        assertEquals(0.5, Money.ofCents(50).ratioTo(Money.ofCents(100)), 1e-12)
        assertEquals(Money.ofCents(5), Money.ofCents(-5).abs())
        assertEquals(listOf(1L, 2L, 3L).map { Money.ofCents(it) }.sumMoney(), Money.ofCents(6))
    }
}
