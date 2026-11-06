package com.arnavgames.tycoon.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Income rate in **micro-cents per second** (equivalently nano-cents per millisecond), exact integer.
 * One cent per second = 1_000_000.
 */
@Serializable(with = RateSerializer::class)
class Rate private constructor(val microCentsPerSecond: BigInteger) : Comparable<Rate> {

    val isZero get() = microCentsPerSecond.signum() == 0
    val isNegative get() = microCentsPerSecond.signum() < 0
    val isPositive get() = microCentsPerSecond.signum() > 0

    operator fun plus(o: Rate) = Rate(microCentsPerSecond.add(o.microCentsPerSecond))
    operator fun minus(o: Rate) = Rate(microCentsPerSecond.subtract(o.microCentsPerSecond))
    operator fun times(m: Multiplier): Rate =
        Rate(BigDecimal(microCentsPerSecond).multiply(m.value, Multiplier.MC).setScale(0, RoundingMode.FLOOR).toBigIntegerExact())

    /** Money earned per whole second (rounded down) — for display. */
    fun perSecond(): Money = Money.ofCents(microCentsPerSecond.divide(MICRO))

    /** Money earned over [seconds] (rounded down) — for display/projections only; accruals use [Accrual]. */
    fun over(seconds: Long): Money = Money.ofCents(microCentsPerSecond.multiply(BigInteger.valueOf(seconds)).divide(MICRO))

    override fun compareTo(other: Rate) = microCentsPerSecond.compareTo(other.microCentsPerSecond)
    override fun equals(other: Any?) = other is Rate && microCentsPerSecond == other.microCentsPerSecond
    override fun hashCode() = microCentsPerSecond.hashCode()
    override fun toString() = "Rate(${perSecond()}/s)"

    companion object {
        internal val MICRO: BigInteger = BigInteger.valueOf(1_000_000)
        val ZERO = Rate(BigInteger.ZERO)

        fun ofMicroCentsPerSecond(v: BigInteger) = Rate(v)
        fun ofMicroCentsPerSecond(v: Long) = Rate(BigInteger.valueOf(v))

        /** From money per second, keeping sub-cent precision (rounded down to micro-cents). */
        fun perSecond(m: Money): Rate = Rate(m.cents.multiply(MICRO))

        /** Base money/s scaled by a multiplier, keeping micro-cent precision. */
        fun perSecond(m: Money, factor: Multiplier): Rate = Rate(
            BigDecimal(m.cents.multiply(MICRO)).multiply(factor.value, Multiplier.MC).setScale(0, RoundingMode.FLOOR).toBigIntegerExact(),
        )
    }
}

object RateSerializer : KSerializer<Rate> {
    override val descriptor = PrimitiveSerialDescriptor("Rate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Rate) = encoder.encodeString(value.microCentsPerSecond.toString())
    override fun deserialize(decoder: Decoder) = Rate.ofMicroCentsPerSecond(BigInteger(decoder.decodeString()))
}

/**
 * Integrates a [Rate] over milliseconds exactly. The remainder below one cent is carried in nano-cents,
 * so `accrue(a + b) == accrue(a) then accrue(b)` holds for every split (property-tested).
 */
@Serializable
data class Accrual(
    /** Carried remainder in nano-cents, always in [0, 1e9). Stored as string for exactness. */
    @Serializable(with = BigIntegerAsString::class) val carryNanoCents: BigInteger = BigInteger.ZERO,
) {
    /** Returns (whole cents earned, new accrual state). rate * ms has units nano-cents. */
    fun accrue(rate: Rate, millis: Long): Pair<Money, Accrual> {
        require(millis >= 0) { "negative time" }
        if (millis == 0L || rate.isZero) return Money.ZERO to this
        require(!rate.isNegative) { "accrual rates must be non-negative; costs are posted separately" }
        val total = rate.microCentsPerSecond.multiply(BigInteger.valueOf(millis)).add(carryNanoCents)
        val qr = total.divideAndRemainder(NANO)
        return Money.ofCents(qr[0]) to Accrual(qr[1])
    }

    /** Milliseconds until at least [target] cents will have accrued (ceil), or null if the rate is zero. */
    fun millisUntil(rate: Rate, target: Money): Long? {
        if (rate.isZero) return null
        if (!target.isPositive) return 0
        val needed = target.cents.multiply(NANO).subtract(carryNanoCents)
        if (needed.signum() <= 0) return 0
        val ms = needed.add(rate.microCentsPerSecond).subtract(BigInteger.ONE).divide(rate.microCentsPerSecond)
        return if (ms.bitLength() < 63) ms.toLong() else Long.MAX_VALUE
    }

    companion object {
        private val NANO: BigInteger = BigInteger.valueOf(1_000_000_000)
    }
}

object BigIntegerAsString : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("BigIntegerString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder) = BigInteger(decoder.decodeString())
}
