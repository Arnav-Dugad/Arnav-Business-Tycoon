package com.arnavgames.tycoon.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact money amount in integer **cents**, from $0.01 to arbitrarily large values.
 *
 * Fast path: values that fit in a Long are stored as a Long (no allocation-heavy BigInteger math).
 * Any overflow promotes transparently to BigInteger; results that fit again are demoted.
 * Never use Double for balances (see docs/ECONOMY.md §1).
 */
@Serializable(with = MoneySerializer::class)
class Money private constructor(private val small: Long, private val big: BigInteger?) : Comparable<Money> {

    val isBig: Boolean get() = big != null

    /** Exact cents as BigInteger. */
    val cents: BigInteger get() = big ?: BigInteger.valueOf(small)

    /** Cents as Long, or null if the value does not fit. */
    val centsOrNull: Long? get() = if (big == null) small else null

    val signum: Int get() = big?.signum() ?: java.lang.Long.signum(small)
    val isZero: Boolean get() = signum == 0
    val isNegative: Boolean get() = signum < 0
    val isPositive: Boolean get() = signum > 0

    operator fun plus(other: Money): Money {
        if (big == null && other.big == null) {
            val r = small + other.small
            // Overflow iff both operands have the same sign and the result's sign differs.
            if (((small xor r) and (other.small xor r)) < 0) return of(cents.add(other.cents))
            return Money(r, null)
        }
        return of(cents.add(other.cents))
    }

    operator fun minus(other: Money): Money {
        if (big == null && other.big == null) {
            val r = small - other.small
            if (((small xor other.small) and (small xor r)) < 0) return of(cents.subtract(other.cents))
            return Money(r, null)
        }
        return of(cents.subtract(other.cents))
    }

    operator fun unaryMinus(): Money =
        if (big == null && small != Long.MIN_VALUE) Money(-small, null) else of(cents.negate())

    operator fun times(factor: Long): Money {
        if (big == null) {
            val hi = Math.multiplyHigh(small, factor)
            val lo = small * factor
            if ((hi == 0L && lo >= 0) || (hi == -1L && lo < 0)) return Money(lo, null)
        }
        return of(cents.multiply(BigInteger.valueOf(factor)))
    }

    /** Multiply by a decimal factor, rounding half-even to whole cents. */
    operator fun times(m: Multiplier): Money = timesExact(m).setScale(0, RoundingMode.HALF_EVEN).let { of(it.toBigIntegerExact()) }

    /** Exact (unrounded) product in cents, used by accruals that keep sub-cent remainders. */
    fun timesExact(m: Multiplier): BigDecimal = BigDecimal(cents).multiply(m.value, MathContext.DECIMAL128)

    /** Integer division in cents, rounding toward zero. */
    operator fun div(divisor: Long): Money {
        require(divisor != 0L) { "division by zero" }
        if (big == null && !(small == Long.MIN_VALUE && divisor == -1L)) return Money(small / divisor, null)
        return of(cents.divide(BigInteger.valueOf(divisor)))
    }

    /** Ratio this / other as a Double (for UI progress bars and heuristics only — never for balances). */
    fun ratioTo(other: Money): Double {
        if (other.isZero) return if (isZero) 0.0 else Double.POSITIVE_INFINITY
        if (big == null && other.big == null) return small.toDouble() / other.small.toDouble()
        return BigDecimal(cents).divide(BigDecimal(other.cents), MathContext.DECIMAL64).toDouble()
    }

    fun abs(): Money = if (isNegative) -this else this
    fun max(other: Money): Money = if (this >= other) this else other
    fun min(other: Money): Money = if (this <= other) this else other

    /** Approximate value in dollars as Double. For charts, animation and log-scale scores only. */
    fun toDollarsDouble(): Double = if (big == null) small / 100.0 else BigDecimal(big).movePointLeft(2).toDouble()

    /** log10 of the dollar value (>= $1), used for leaderboards and pacing metrics. Returns 0 for values < $1. */
    fun log10Dollars(): Double {
        if (signum <= 0) return 0.0
        val c = cents
        val bits = c.bitLength()
        val log10Cents = if (bits < 63) kotlin.math.log10(c.toDouble()) else {
            val shift = bits - 60
            kotlin.math.log10(c.shiftRight(shift).toDouble()) + shift * kotlin.math.log10(2.0)
        }
        return (log10Cents - 2.0).coerceAtLeast(0.0)
    }

    override fun compareTo(other: Money): Int {
        if (big == null && other.big == null) return small.compareTo(other.small)
        return cents.compareTo(other.cents)
    }

    override fun equals(other: Any?): Boolean = other is Money && compareTo(other) == 0
    override fun hashCode(): Int = if (big == null) small.hashCode() else big.hashCode()

    /** Canonical, exact representation: cents as a decimal integer string (used in saves). */
    fun toCentsString(): String = big?.toString() ?: small.toString()

    override fun toString(): String = MoneyFormat.full(this)

    companion object {
        private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
        private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)

        val ZERO = Money(0, null)
        val ONE_CENT = Money(1, null)

        fun ofCents(cents: Long): Money = Money(cents, null)
        fun ofCents(cents: BigInteger): Money = of(cents)
        fun ofDollars(dollars: Long): Money = ofCents(dollars) * 100L

        /** Parses "123" (cents string). For human input use [MoneyFormat.parseDollars]. */
        fun parseCents(text: String): Money = of(BigInteger(text.trim()))

        /** Exact dollars from a decimal, rounded half-even to cents. */
        fun ofDollars(dollars: BigDecimal): Money = of(dollars.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).toBigIntegerExact())

        internal fun of(v: BigInteger): Money =
            if (v >= LONG_MIN && v <= LONG_MAX) Money(v.toLong(), null) else Money(0, v)
    }
}

/** Serialized as the exact cents string, e.g. "123456" — never as a floating number. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Money) = encoder.encodeString(value.toCentsString())
    override fun deserialize(decoder: Decoder): Money {
        val m = Money.parseCents(decoder.decodeString())
        val f = scaleOnRead.get() ?: return m
        return Money.ofDollars(java.math.BigDecimal(m.cents).movePointLeft(2).multiply(f, java.math.MathContext.DECIMAL128))
    }

    /** Set only while converting an old save to real-world money (EconomyConversion): every amount read is scaled. */
    val scaleOnRead: ThreadLocal<java.math.BigDecimal?> = ThreadLocal()
}

fun Iterable<Money>.sumMoney(): Money = fold(Money.ZERO) { acc, m -> acc + m }
