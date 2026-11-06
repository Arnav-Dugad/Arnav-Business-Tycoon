package com.arnavgames.tycoon.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Rate
import com.arnavgames.tycoon.designsystem.theme.Aurum
import java.math.BigInteger

/**
 * A money counter that rolls smoothly at frame rate between engine ticks.
 *
 * The engine updates [value] about once per second; between updates the counter extrapolates using [rate]
 * (capped at [maxExtrapolationMs] so it never runs ahead of reality). When [value] jumps (a purchase or reward),
 * the counter glides to it over [jumpMs]. Reduced motion shows exact values without animation.
 * The accessibility label always announces the exact engine value, not the animated one.
 */
@Composable
fun RollingMoney(
    value: Money,
    rate: Rate,
    modifier: Modifier = Modifier,
    style: TextStyle = Aurum.type.numberHero,
    color: Color = Aurum.colors.textPrimary,
    format: (Money) -> String = { MoneyFormat.short(it) },
    maxExtrapolationMs: Long = 1_200,
    jumpMs: Long = 450,
) {
    val reduced = Aurum.reducedMotion
    var anchorValue by remember { mutableStateOf(value) }
    var anchorTime by remember { mutableLongStateOf(0L) }
    var glideFrom by remember { mutableStateOf<Money?>(null) }
    var shown by remember { mutableStateOf(value) }

    LaunchedEffect(value, rate, reduced) {
        val start = withFrameMillis { it }
        // A change that differs from the extrapolated value by more than ~2 s of income is a "jump" → glide to it.
        val expected = extrapolate(anchorValue, rate, start - anchorTime, maxExtrapolationMs)
        val gap = (value - expected).abs()
        glideFrom = if (!reduced && anchorTime != 0L && gap > rate.over(2).max(Money.ONE_CENT)) shown else null
        anchorValue = value
        anchorTime = start
        if (reduced) { shown = value; return@LaunchedEffect }
        // Animate only while there is something to show: income rolling or a glide in progress.
        while (true) {
            val done = withFrameMillis { now ->
                val elapsed = now - anchorTime
                val target = extrapolate(anchorValue, rate, elapsed, maxExtrapolationMs)
                val from = glideFrom
                if (from != null && elapsed < jumpMs) {
                    shown = lerp(from, target, easeOut(elapsed.toDouble() / jumpMs))
                    false
                } else {
                    glideFrom = null
                    shown = target
                    rate.isZero || elapsed >= maxExtrapolationMs
                }
            }
            if (done) break
        }
    }
    val display = if (reduced) value else shown
    Text(
        text = format(display),
        style = style,
        color = color,
        modifier = modifier.semantics { contentDescription = MoneyFormat.short(value) },
        maxLines = 1,
        softWrap = false,
    )
}

private fun extrapolate(base: Money, rate: Rate, elapsedMs: Long, cap: Long): Money {
    if (rate.isZero || elapsedMs <= 0) return base
    val ms = elapsedMs.coerceAtMost(cap)
    // rate µ¢/s × ms = nano-cents → cents
    val cents = rate.microCentsPerSecond.multiply(BigInteger.valueOf(ms)).divide(BigInteger.valueOf(1_000_000_000))
    return base + Money.ofCents(cents)
}

private fun easeOut(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return 1 - (1 - x) * (1 - x) * (1 - x)
}

/** Interpolation for display only (exact endpoints, approximate middle). */
private fun lerp(a: Money, b: Money, t: Double): Money {
    if (t >= 1.0) return b
    val diff = b - a
    val step = java.math.BigDecimal(diff.cents).multiply(java.math.BigDecimal(t)).toBigInteger()
    return a + Money.ofCents(step)
}
