package com.arnavgames.tycoon.ui.markets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arnavgames.tycoon.core.content.AssetClass
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Tiny line chart for list rows. Color carries direction, but the row also shows a signed change label. */
@Composable
fun Sparkline(points: List<Float>, up: Boolean, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val color = if (up) c.gain else c.loss
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val path = linePath(points, size, padding = 2f)
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Price chart: a line with a soft gradient fill, a dashed baseline at the opening price and min/max labels.
 * Drawn with Canvas only (no chart library).
 */
@Composable
fun PriceChart(points: List<Float>, rangeLabel: String, change: Double, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val up = change >= 0
    val line = if (up) c.gain else c.loss
    val desc = "Price chart, $rangeLabel, ${if (up) "up" else "down"} ${String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(change) * 100)} percent"
    Canvas(modifier.semantics { contentDescription = desc }) {
        if (points.size < 2) return@Canvas
        val pad = 6.dp.toPx()
        val path = linePath(points, size, pad)
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width - pad, size.height)
            lineTo(pad, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0.0f))))
        // Baseline at the first point.
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val y0 = pad + (size.height - 2 * pad) * (1 - (points.first() - min) / span)
        drawLine(c.separator, Offset(pad, y0), Offset(size.width - pad, y0), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawPath(path, line, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Current-price dot.
        val last = Offset(size.width - pad, pad + (size.height - 2 * pad) * (1 - (points.last() - min) / span))
        drawCircle(line.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = last)
        drawCircle(line, radius = 3.5.dp.toPx(), center = last)
    }
}

private fun DrawScope.linePath(points: List<Float>, size: Size, padding: Float): Path {
    val min = points.min()
    val max = points.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val w = size.width - 2 * padding
    val h = size.height - 2 * padding
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = padding + w * i / (points.size - 1)
        val y = padding + h * (1 - (p - min) / span)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

/**
 * Procedural logo for a fictional company or coin: a continuous-corner tile in a muted hue derived from the
 * ticker, one of eight geometric marks, and the ticker's initial. Original and generated in code (no images).
 */
@Composable
fun TickerLogo(ticker: String, assetClass: AssetClass, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = Aurum.colors
    val seed = remember(ticker) { ticker.fold(7) { a, ch -> a * 31 + ch.code } }
    val hue = ((seed and 0x7FFFFFFF) % 360).toFloat()
    val base = Color.hsl(hue, if (c.isDark) 0.32f else 0.38f, if (c.isDark) 0.30f else 0.42f)
    val hi = Color.hsl((hue + 24f) % 360f, 0.40f, if (c.isDark) 0.42f else 0.56f)
    val mark = Color.White.copy(alpha = 0.92f)
    val shape = if (assetClass == AssetClass.Crypto) AurumShapes.pill else AurumShapes.small
    Box(
        modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(hi, base))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val ctr = center
            val sw = r * 0.10f
            when ((seed ushr 3) and 7) {
                0 -> drawCircle(mark.copy(alpha = 0.35f), radius = r * 0.62f, center = ctr, style = Stroke(sw))
                1 -> drawPath(Path().apply { moveTo(ctr.x, ctr.y - r * 0.62f); lineTo(ctr.x + r * 0.62f, ctr.y); lineTo(ctr.x, ctr.y + r * 0.62f); lineTo(ctr.x - r * 0.62f, ctr.y); close() }, mark.copy(alpha = 0.30f), style = Stroke(sw))
                2 -> for (k in 0..2) drawLine(mark.copy(alpha = 0.28f), Offset(ctr.x - r * 0.55f, ctr.y + r * (0.5f - k * 0.5f)), Offset(ctr.x + r * 0.55f, ctr.y + r * (0.2f - k * 0.5f)), strokeWidth = sw)
                3 -> drawArc(mark.copy(alpha = 0.35f), -200f, 220f, false, topLeft = Offset(ctr.x - r * 0.6f, ctr.y - r * 0.6f), size = Size(r * 1.2f, r * 1.2f), style = Stroke(sw, cap = StrokeCap.Round))
                4 -> drawPath(Path().apply {
                    for (k in 0..5) {
                        val a = PI / 3 * k - PI / 2
                        val p = Offset(ctr.x + (r * 0.62f * cos(a)).toFloat(), ctr.y + (r * 0.62f * sin(a)).toFloat())
                        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }, mark.copy(alpha = 0.30f), style = Stroke(sw))
                5 -> drawPath(Path().apply { moveTo(ctr.x - r * 0.55f, ctr.y + r * 0.35f); lineTo(ctr.x, ctr.y - r * 0.4f); lineTo(ctr.x + r * 0.55f, ctr.y + r * 0.35f) }, mark.copy(alpha = 0.32f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                6 -> for (k in 0..2) drawCircle(mark.copy(alpha = 0.30f), radius = r * 0.12f, center = Offset(ctr.x + r * 0.42f * (k - 1), ctr.y + r * 0.45f))
                else -> drawRoundRect(mark.copy(alpha = 0.28f), topLeft = Offset(ctr.x - r * 0.5f, ctr.y - r * 0.5f), size = Size(r, r), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.3f), style = Stroke(sw))
            }
        }
        Text(ticker.take(1), color = mark, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp, style = Aurum.type.title)
    }
}
