package com.arnavgames.tycoon.core.money

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact decimal factor (34 significant digits, DECIMAL128) for income multipliers, discounts and rates.
 * Stacking thousands of multipliers stays exact enough that no drift is visible in cents.
 */
@Serializable(with = MultiplierSerializer::class)
class Multiplier private constructor(val value: BigDecimal) : Comparable<Multiplier> {

    operator fun times(other: Multiplier) = Multiplier(value.multiply(other.value, MC))
    operator fun plus(other: Multiplier) = Multiplier(value.add(other.value, MC))
    operator fun minus(other: Multiplier) = Multiplier(value.subtract(other.value, MC))
    operator fun div(other: Multiplier) = Multiplier(value.divide(other.value, MC))

    /** this^n for integer n >= 0 (used for exponential level costs). */
    fun pow(n: Int): Multiplier {
        require(n >= 0)
        return Multiplier(value.pow(n, MC))
    }

    fun max(other: Multiplier) = if (this >= other) this else other
    fun min(other: Multiplier) = if (this <= other) this else other
    fun coerceIn(lo: Multiplier, hi: Multiplier) = max(lo).min(hi)

    fun toDouble(): Double = value.toDouble()

    override fun compareTo(other: Multiplier) = value.compareTo(other.value)
    override fun equals(other: Any?) = other is Multiplier && value.compareTo(other.value) == 0
    override fun hashCode() = value.stripTrailingZeros().hashCode()

    /** "×2.5", "×1.08" — trailing zeros trimmed, max 3 decimals. */
    fun display(): String = "×" + value.setScale(3, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()

    override fun toString(): String = value.toPlainString()

    companion object {
        val MC: MathContext = MathContext.DECIMAL128
        val ONE = Multiplier(BigDecimal.ONE)
        val ZERO = Multiplier(BigDecimal.ZERO)

        fun of(text: String) = Multiplier(BigDecimal(text, MC))
        fun of(value: BigDecimal) = Multiplier(value.round(MC))
        fun of(value: Long) = Multiplier(BigDecimal.valueOf(value))

        /** From a Double that came from content JSON or a random draw; converted via its decimal string. */
        fun ofDouble(value: Double): Multiplier {
            require(value.isFinite()) { "multiplier must be finite: $value" }
            return Multiplier(BigDecimal(value.toString(), MC))
        }

        /** 1 + percent/100, e.g. percent(15) = ×1.15 */
        fun percent(p: Double) = ofDouble(1.0 + p / 100.0)

        fun product(items: Iterable<Multiplier>): Multiplier = items.fold(ONE) { a, b -> a * b }
    }
}

object MultiplierSerializer : KSerializer<Multiplier> {
    override val descriptor = PrimitiveSerialDescriptor("Multiplier", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Multiplier) = encoder.encodeString(value.value.toPlainString())
    override fun deserialize(decoder: Decoder): Multiplier = Multiplier.of(decoder.decodeString())
}
