package com.arnavgames.tycoon.core.money

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger

class AccrualTest {
    @Test
    fun splitInvariance(): Unit = runBlocking {
        checkAll(5000, Arb.long(0, 10_000_000_000_000L), Arb.long(0, 50_000_000), Arb.long(0, 50_000_000)) { r, a, b ->
            val rate = Rate.ofMicroCentsPerSecond(r)
            val (whole, accWhole) = Accrual().accrue(rate, a + b)
            val (p1, acc1) = Accrual().accrue(rate, a)
            val (p2, acc2) = acc1.accrue(rate, b)
            assertEquals(whole, p1 + p2)
            assertEquals(accWhole, acc2)
        }
    }

    @Test
    fun exactTotalOverManySmallSteps() {
        val rate = Rate.perSecond(Money.ofCents(1), Multiplier.of("0.333333")) // 0.333333 c/s
        var acc = Accrual()
        var total = Money.ZERO
        repeat(36_000) { // 10 hours in 1 s steps
            val (m, a) = acc.accrue(rate, 1000)
            total += m; acc = a
        }
        val (bulk, _) = Accrual().accrue(rate, 36_000_000)
        assertEquals(bulk, total)
        assertEquals(Money.ofCents(11_999), bulk) // 0.333333 * 36000 = 11999.988
    }

    @Test
    fun millisUntilIsTheFirstInstantTargetIsReached(): Unit = runBlocking {
        checkAll(3000, Arb.long(1, 1_000_000_000_000L), Arb.long(1, 1_000_000_000L)) { r, targetCents ->
            val rate = Rate.ofMicroCentsPerSecond(r)
            val target = Money.ofCents(targetCents)
            val ms = Accrual().millisUntil(rate, target)!!
            if (ms < 1_000_000_000_000L) {
                assertTrue(Accrual().accrue(rate, ms).first >= target)
                if (ms > 0) assertTrue(Accrual().accrue(rate, ms - 1).first < target)
            }
        }
    }

    @Test
    fun hugeRatesStayExact() {
        val rate = Rate.ofMicroCentsPerSecond(BigInteger.TEN.pow(80))
        val (m, acc) = Accrual().accrue(rate, 1)
        assertEquals(BigInteger.TEN.pow(71), m.cents) // 1e80 µ¢/s × 1 ms = 1e80 nano-cents = 1e71 cents
        assertEquals(BigInteger.ZERO, acc.carryNanoCents)
    }
}
