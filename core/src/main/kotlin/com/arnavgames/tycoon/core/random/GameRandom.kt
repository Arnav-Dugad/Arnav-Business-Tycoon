package com.arnavgames.tycoon.core.random

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Seeded, reproducible randomness (docs/ARCHITECTURE.md §3.2).
 * PCG32 (XSH-RR) generator; independent named streams are derived with SplitMix64 from
 * (worldSeed, streamName, key), e.g. `GameRandom.stream(seed, "market", tick)`.
 * Rules code must never use kotlin.random.Random.Default (checked by a test).
 */
class Pcg32(seed: Long, sequence: Long) {
    private var state = 0L
    private val inc = (sequence shl 1) or 1L

    init {
        nextInt()
        state += seed
        nextInt()
    }

    fun nextInt(): Int {
        val old = state
        state = old * MULT + inc
        val xorShifted = (((old ushr 18) xor old) ushr 27).toInt()
        val rot = (old ushr 59).toInt()
        return Integer.rotateRight(xorShifted, rot)
    }

    /** Uniform in [0, bound) without modulo bias. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val b = bound.toLong()
        val threshold = (0x1_0000_0000L - b) % b
        while (true) {
            val r = nextInt().toLong() and 0xFFFF_FFFFL
            if (r >= threshold) return (r % b).toInt()
        }
    }

    fun nextInt(from: Int, untilExclusive: Int): Int = from + nextInt(untilExclusive - from)

    fun nextLong(): Long = (nextInt().toLong() shl 32) or (nextInt().toLong() and 0xFFFF_FFFFL)

    /** Uniform in [0, 1) with 53 bits of precision. */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun nextDouble(from: Double, until: Double) = from + (until - from) * nextDouble()

    fun chance(p: Double): Boolean = nextDouble() < p

    /** Standard normal via Box–Muller (one value per call; deterministic). */
    fun nextGaussian(): Double {
        var u1 = nextDouble()
        while (u1 <= 1e-300) u1 = nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    /** Weighted pick; weights must be non-negative and not all zero. */
    fun <T> pickWeighted(items: List<T>, weight: (T) -> Double): T {
        val total = items.sumOf(weight)
        require(total > 0)
        var r = nextDouble() * total
        for (item in items) {
            r -= weight(item)
            if (r < 0) return item
        }
        return items.last()
    }

    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        return out
    }

    private companion object {
        const val MULT = 6364136223846793005L
    }
}

object GameRandom {
    /** A reproducible generator for one (stream, key) pair of a world. */
    fun stream(worldSeed: Long, streamName: String, key: Long = 0): Pcg32 {
        val s = splitMix(worldSeed xor splitMix(fnv1a(streamName)) xor splitMix(key * GOLDEN))
        return Pcg32(s, splitMix(s xor fnv1a(streamName)))
    }

    /** Fresh world seed from an entropy source supplied by the caller (app: SecureRandom). */
    fun newWorldSeed(entropy: Long): Long = splitMix(entropy)

    fun splitMix(x: Long): Long {
        var z = x + GOLDEN
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun fnv1a(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in text) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    private const val GOLDEN = -0x61c8864680b583ebL
}
