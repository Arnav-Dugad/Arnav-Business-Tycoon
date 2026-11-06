package com.arnavgames.tycoon.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Continuous ("squircle") rounded rectangle. Instead of a circular arc that meets the straight edge abruptly,
 * each corner starts curving earlier and blends into a shorter arc through two cubic Béziers, giving the soft
 * silhouette of premium hardware and modern desktop/mobile UIs.
 *
 * [smoothing] 0 = ordinary rounded rectangle, 0.6 = the classic "continuous" look, 1 = maximum smoothing.
 * When the shape is too small for the requested corner, smoothing and then radius are reduced so corners never overlap.
 */
@Immutable
class SmoothCornerShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val maxP = min(size.width, size.height) / 2f
        var r = with(density) { radius.toPx() }.coerceAtLeast(0f)
        var s = smoothing.coerceIn(0f, 1f)
        if (r <= 0f || maxP <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        if (r > maxP) { r = maxP; s = 0f }
        if (r * (1 + s) > maxP) s = (maxP / r - 1f).coerceIn(0f, 1f)
        val g = CornerGeometry.of(r, s)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(g.p, 0f)
            // Top edge → top-right corner
            lineTo(w - g.p, 0f)
            corner(this, g, w, 0f, startAngle = -90f)
            // Right edge → bottom-right corner
            lineTo(w, h - g.p)
            corner(this, g, w, h, startAngle = 0f)
            // Bottom edge → bottom-left corner
            lineTo(g.p, h)
            corner(this, g, 0f, h, startAngle = 90f)
            // Left edge → top-left corner
            lineTo(0f, g.p)
            corner(this, g, 0f, 0f, startAngle = 180f)
            close()
        }
        return Outline.Generic(path)
    }

    /**
     * Draws one corner. ([cx],[cy]) is the rectangle's corner point.
     * The corner is traced clockwise: Bézier from the incoming edge, the circular arc, Bézier to the outgoing edge.
     */
    private fun corner(path: Path, g: CornerGeometry, cx: Float, cy: Float, startAngle: Float) {
        // Local frame: "u" runs along the incoming edge toward the corner, "v" along the outgoing edge away from it.
        // For clockwise traversal the incoming edge direction and outgoing direction depend on the corner.
        fun pt(alongIncoming: Float, alongOutgoing: Float): Offset = when (startAngle) {
            -90f -> Offset(cx - alongIncoming, cy + alongOutgoing)  // top-right: incoming = top edge (→), outgoing = right edge (↓)
            0f -> Offset(cx - alongOutgoing, cy - alongIncoming)    // bottom-right: incoming = right edge (↓), outgoing = bottom edge (←)
            90f -> Offset(cx + alongIncoming, cy - alongOutgoing)   // bottom-left: incoming = bottom edge (←), outgoing = left edge (↑)
            else -> Offset(cx + alongOutgoing, cy + alongIncoming)  // top-left: incoming = left edge (↑), outgoing = top edge (→)
        }
        // Points measured from the corner: the curve starts p away along the incoming edge.
        val c1 = pt(g.p - g.a, 0f)
        val c2 = pt(g.p - g.a - g.b, 0f)
        val arcStart = pt(g.p - g.a - g.b - g.c, g.d)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, arcStart.x, arcStart.y)
        // Circular arc centred r in from both edges.
        val center = pt(g.r, g.r)
        val rect = Rect(center.x - g.r, center.y - g.r, center.x + g.r, center.y + g.r)
        path.arcTo(rect, startAngle + g.angleAlpha, g.arcMeasure, false)
        val c3 = pt(0f, g.p - g.a - g.b)
        val c4 = pt(0f, g.p - g.a)
        val end = pt(0f, g.p)
        path.cubicTo(c3.x, c3.y, c4.x, c4.y, end.x, end.y)
    }

    override fun equals(other: Any?) = other is SmoothCornerShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = radius.hashCode() * 31 + smoothing.hashCode()
    override fun toString() = "SmoothCornerShape($radius, $smoothing)"

    /** Precomputed corner measurements (all in px, angles in degrees). */
    private data class CornerGeometry(
        val r: Float, val p: Float, val a: Float, val b: Float, val c: Float, val d: Float,
        val arcMeasure: Float, val angleAlpha: Float,
    ) {
        companion object {
            private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

            fun of(r: Float, s: Float): CornerGeometry {
                val p = (1 + s) * r
                val arcMeasure = 90f * (1 - s)
                val arcSectionLength = (sin(rad(arcMeasure / 2)) * r * sqrt(2.0)).toFloat()
                val angleAlpha = (90f - arcMeasure) / 2f
                val p3ToP4 = (r * tan(rad(angleAlpha / 2))).toFloat()
                val angleBeta = 45f * s
                val c = (p3ToP4 * cos(rad(angleBeta))).toFloat()
                val d = (c * tan(rad(angleBeta))).toFloat()
                val b = (p - arcSectionLength - c - d) / 3f
                val a = 2f * b
                return CornerGeometry(r, p, a, b, c, d, arcMeasure, angleAlpha)
            }
        }
    }
}

/** Shorthand: `Smooth(24.dp)`. */
fun Smooth(radius: Dp, smoothing: Float = 0.6f) = SmoothCornerShape(radius, smoothing)

val SmoothDefault = SmoothCornerShape(22.dp)
