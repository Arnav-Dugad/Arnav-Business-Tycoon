package com.arnavgames.tycoon.core.random

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GameRandomTest {
    @Test
    fun sameSeedSameSequence() {
        val a = GameRandom.stream(123, "market", 7)
        val b = GameRandom.stream(123, "market", 7)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun streamsAndKeysAreIndependent() {
        val base = List(64) { GameRandom.stream(123, "market", 7).let { r -> repeat(it) { r.nextInt() }; r.nextInt() } }
        val otherKey = List(64) { GameRandom.stream(123, "market", 8).let { r -> repeat(it) { r.nextInt() }; r.nextInt() } }
        val otherStream = List(64) { GameRandom.stream(123, "events", 7).let { r -> repeat(it) { r.nextInt() }; r.nextInt() } }
        assertNotEquals(base, otherKey)
        assertNotEquals(base, otherStream)
    }

    @Test
    fun distributionsAreSane() {
        val r = GameRandom.stream(99, "dist")
        val n = 200_000
        var sum = 0.0; var sumSq = 0.0
        val buckets = IntArray(10)
        repeat(n) {
            val g = r.nextGaussian(); sum += g; sumSq += g * g
            buckets[r.nextInt(10)]++
            val d = r.nextDouble(); assertTrue(d >= 0.0 && d < 1.0)
        }
        assertEquals(0.0, sum / n, 0.02)
        assertEquals(1.0, sumSq / n, 0.02)
        buckets.forEach { assertEquals(n / 10.0, it.toDouble(), n * 0.01) }
    }

    @Test
    fun rulesCodeNeverUsesDefaultRandom() {
        val root = File("src/main/kotlin")
        val offenders = root.walk().filter { it.isFile && it.extension == "kt" && it.name != "GameRandom.kt" }
            .filter { f -> f.readText().let { it.contains("Random.Default") || it.contains("kotlin.random.Random") || it.contains("Math.random") || it.contains("java.util.Random") } }
            .map { it.path }.toList()
        assertEquals(emptyList<String>(), offenders, "use GameRandom streams for reproducible simulations")
    }
}
