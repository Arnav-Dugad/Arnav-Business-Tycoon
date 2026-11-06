package com.arnavgames.tycoon.core.state

import com.arnavgames.tycoon.core.money.Money
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigInteger

/**
 * An exact quantity of shares (or coins, or units) in millionths. Late-game positions exceed Long range,
 * so this is a BigInteger; trades are rare enough that the cost does not matter.
 */
@Serializable(with = QtySerializer::class)
class Qty(val micro: BigInteger) : Comparable<Qty> {
    val isZero: Boolean get() = micro.signum() == 0
    val isPositive: Boolean get() = micro.signum() > 0
    operator fun plus(o: Qty) = Qty(micro + o.micro)
    operator fun minus(o: Qty) = Qty(micro - o.micro)
    override fun compareTo(other: Qty) = micro.compareTo(other.micro)
    override fun equals(other: Any?) = other is Qty && micro == other.micro
    override fun hashCode() = micro.hashCode()
    override fun toString() = micro.toString()
    fun toDouble(): Double = micro.toDouble() / 1e6

    /** Market value at a price in micro-dollars per unit, floored to the cent. */
    fun valueAt(priceMicros: Long): Money = Money.ofCents(micro.multiply(BigInteger.valueOf(priceMicros)).divide(TEN_BILLION))

    /** The part of this quantity given by num/den (floored). */
    fun fraction(num: Long, den: Long): Qty = Qty(micro.multiply(BigInteger.valueOf(num)).divide(BigInteger.valueOf(den)))

    companion object {
        val ZERO = Qty(BigInteger.ZERO)
        private val TEN_BILLION: BigInteger = BigInteger.TEN.pow(10)

        /** How many units [amount] buys at [priceMicros] per unit (floored to a millionth). */
        fun affordable(amount: Money, priceMicros: Long): Qty {
            require(priceMicros > 0)
            return Qty(amount.cents.multiply(TEN_BILLION).divide(BigInteger.valueOf(priceMicros)))
        }
    }
}

object QtySerializer : KSerializer<Qty> {
    override val descriptor = PrimitiveSerialDescriptor("Qty", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Qty) = encoder.encodeString(value.micro.toString())
    override fun deserialize(decoder: Decoder): Qty = Qty(BigInteger(decoder.decodeString()))
}

/**
 * The market: quotes, the macro cycle, news, and the player's positions (docs/ECONOMY.md §5).
 * Quotes depend only on the world seed and the minute index, never on the player's orders, so the market is
 * identical however the time between two moments is split (live play, offline catch-up, simulator).
 */
@Serializable
data class MarketState(
    /** Minute index of the last processed market tick (-1 = not started). */
    val tick: Long = -1,
    val macro: MacroState = MacroState(),
    val quotes: Map<String, Quote> = emptyMap(),
    val holdings: Map<String, Holding> = emptyMap(),
    val shorts: Map<String, ShortPosition> = emptyMap(),
    val news: List<NewsItem> = emptyList(),
    val hints: List<EarningsHint> = emptyList(),
    val sectorDrift: Map<String, SectorDrift> = emptyMap(),
    val nextNewsId: Long = 1,
    /** Lifetime number of completed trades (missions and stats). */
    val trades: Long = 0,
    /** Chapter whose depth scale the market currently uses (dilution news is posted when it grows). */
    val depthChapter: Int = 1,
)

@Serializable
data class MacroState(val phase: String = "", val phaseEndsTick: Long = 0)

/**
 * Dynamic state of one instrument. Price = exp(logV + x): logV is the fundamental value (a random walk with
 * drift), x is mean-reverting noise plus news jumps. History is kept at two resolutions for charts.
 */
@Serializable
data class Quote(
    val logV: Double,
    val x: Double = 0.0,
    /** Log-prices ×10⁴ every historyFineMinutes, oldest first (see [PriceHistory]). */
    val fine: @Serializable(with = PackedIntsSerializer::class) List<Int> = emptyList(),
    /** Log-prices ×10⁴ every historyCoarseMinutes, oldest first. */
    val coarse: @Serializable(with = PackedIntsSerializer::class) List<Int> = emptyList(),
    /** Crypto hype: extra drift per day until [hypeUntil] (minute index). */
    val hypeDrift: Double = 0.0,
    val hypeUntil: Long = 0,
) {
    val price: Double get() = kotlin.math.exp(logV + x)
}

/** Chart history is stored as round(ln(price) × 10⁴): exact integers (0.01 % resolution) that pack small. */
object PriceHistory {
    fun key(price: Double): Int = kotlin.math.round(kotlin.math.ln(price) * 10_000.0).toInt()
    fun price(key: Int): Double = kotlin.math.exp(key / 10_000.0)
}

/** Packs a list of Ints as base64 zig-zag varint deltas: lossless, and about 1–2 bytes per chart point. */
object PackedIntsSerializer : KSerializer<List<Int>> {
    override val descriptor = PrimitiveSerialDescriptor("PackedInts", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: List<Int>) {
        val out = java.io.ByteArrayOutputStream(value.size * 2)
        var prev = 0L
        for (v in value) {
            val d = v.toLong() - prev
            var z = (d shl 1) xor (d shr 63)
            while (true) {
                if (z and 0x7FL.inv() == 0L) { out.write(z.toInt()); break }
                out.write(((z and 0x7F) or 0x80).toInt()); z = z ushr 7
            }
            prev = v.toLong()
        }
        encoder.encodeString(java.util.Base64.getEncoder().withoutPadding().encodeToString(out.toByteArray()))
    }
    override fun deserialize(decoder: Decoder): List<Int> {
        val bytes = java.util.Base64.getDecoder().decode(decoder.decodeString())
        val out = ArrayList<Int>(bytes.size)
        var i = 0
        var prev = 0L
        while (i < bytes.size) {
            var shift = 0
            var z = 0L
            while (true) {
                val b = bytes[i++].toLong() and 0xFF
                z = z or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0L) break
                shift += 7
            }
            val d = (z ushr 1) xor -(z and 1)
            prev += d
            out += prev.toInt()
        }
        return out
    }
}

@Serializable
data class Holding(val qty: Qty, val costBasis: Money)

/**
 * A short sale: [qty] borrowed units sold at [entryValue]. [collateral] (sale proceeds plus the player's margin)
 * is locked until the position is covered; a forced cover happens when collateral falls below the maintenance margin.
 */
@Serializable
data class ShortPosition(val qty: Qty, val entryValue: Money, val collateral: Money, val openedTick: Long)

@Serializable
data class NewsItem(
    val id: Long,
    val tick: Long,
    val headline: String,
    val kind: String,
    /** Instrument or sector the story is about ("" for market-wide). */
    val target: String = "",
    /** Price effect applied, e.g. 0.06 = +6% (0 for hints, whose effect arrives at earnings). */
    val effect: Double = 0.0,
    /** "Rumor", "Report" or "Confirmed" for predictive stories. */
    val reliability: String = "",
)

@Serializable
data class EarningsHint(val stockId: String, val magnitude: Double, val reliability: String, val tick: Long)

@Serializable
data class SectorDrift(val perDay: Double, val untilTick: Long)
