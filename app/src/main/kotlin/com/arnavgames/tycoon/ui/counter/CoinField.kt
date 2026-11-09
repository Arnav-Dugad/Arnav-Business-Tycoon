package com.arnavgames.tycoon.ui.counter

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A small physics system for the counter's coins, stored in flat arrays (no per-coin objects, no allocation per frame).
 * Each coin bursts out of a touch, falls under gravity while spinning, then is pulled into the cash total by a spring
 * attractor. [step] returns how many coins arrived this frame (the cash readout pulses for them).
 */
class CoinField(private val capacity: Int = 420) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    val age = FloatArray(capacity)
    val spin = FloatArray(capacity)
    private val spinRate = FloatArray(capacity)
    val size = FloatArray(capacity)
    var count = 0
        private set

    /** Burst [n] coins from (px, py). [power] scales speed (frenzies and combos throw harder). [scale] is px per dp. */
    fun burst(px: Float, py: Float, n: Int, power: Float, scale: Float, rnd: Random) {
        repeat(n) {
            if (count >= capacity) return
            val i = count++
            // Mostly upward, fanned out.
            val angle = (-PI / 2 + (rnd.nextFloat() - 0.5f) * PI * 0.9).toFloat()
            val speed = (380f + rnd.nextFloat() * 520f) * power * scale
            x[i] = px; y[i] = py
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            age[i] = 0f
            spin[i] = rnd.nextFloat() * (2 * PI).toFloat()
            spinRate[i] = (6f + rnd.nextFloat() * 10f) * if (rnd.nextBoolean()) 1 else -1
            size[i] = (9f + rnd.nextFloat() * 7f) * scale
        }
    }

    /** Advances [dt] seconds toward the target (tx, ty). Returns the number of coins that reached it. */
    fun step(dt: Float, tx: Float, ty: Float, scale: Float): Int {
        val gravity = 2100f * scale
        var arrived = 0
        var i = 0
        while (i < count) {
            age[i] += dt
            spin[i] += spinRate[i] * dt
            if (age[i] < FLIGHT) {
                vy[i] += gravity * dt
                vx[i] *= 1f - 0.8f * dt
            } else {
                // Spring toward the cash readout, getting stronger the longer the coin has flown.
                val dx = tx - x[i]
                val dy = ty - y[i]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val pull = (5200f + 9000f * (age[i] - FLIGHT)) * scale
                vx[i] += dx / dist * pull * dt
                vy[i] += dy / dist * pull * dt
                val damp = 1f - 3.2f * dt
                vx[i] *= damp; vy[i] *= damp
                if (dist < 22f * scale || age[i] > LIFETIME) {
                    if (age[i] <= LIFETIME) arrived++
                    remove(i)
                    continue
                }
            }
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
        return arrived
    }

    fun clear() { count = 0 }

    private fun remove(i: Int) {
        val last = count - 1
        if (i != last) {
            x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
            age[i] = age[last]; spin[i] = spin[last]; spinRate[i] = spinRate[last]; size[i] = size[last]
        }
        count--
    }

    companion object {
        /** Seconds of free flight before the attractor takes over. */
        const val FLIGHT = 0.42f
        /** Safety net: a coin is retired after this long whatever happens. */
        const val LIFETIME = 2.4f
    }
}
