package com.arnavgames.tycoon.designsystem.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * Aurum Glass: frosted, translucent surfaces with real backdrop blur.
 *
 * Layering (Haze "Behind" selection uses zIndex):
 *  - [GlassLayer.Backdrop] (z = 0): the ambient glow behind everything.
 *  - [GlassLayer.Content]  (z = 1): scrolling screen content.
 *  - Chrome (headers, tab bar, sheets, dialogs) sits outside both and blurs everything beneath it.
 *  - Glass cards inside the content blur only the backdrop, so nothing ever samples itself.
 *
 * Real blur needs Android 12+ (RenderEffect). On Android 9–11 the same surfaces use a frosted
 * translucent fill instead, which keeps the look without the cost.
 */
val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

enum class GlassLayer(val z: Float) { Backdrop(0f), Content(1f) }

enum class GlassWeight { Thin, Regular, Thick }

object Glass {
    /** Whether real backdrop blur is used on this device. */
    val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val state: HazeState?
        @Composable @ReadOnlyComposable get() = LocalGlassState.current
}

/** Marks this composable's drawing as something glass above it can blur. */
@Composable
fun Modifier.glassSource(layer: GlassLayer): Modifier {
    val state = LocalGlassState.current ?: return this
    return this.hazeSource(state, zIndex = layer.z)
}

@Composable
private fun blurStyle(weight: GlassWeight): HazeBlurStyle {
    val c = Aurum.colors
    return remember(c, weight) {
        val tint = when (weight) {
            GlassWeight.Thin -> c.glassFill
            GlassWeight.Regular -> c.glassFill.compositeOver(c.background.copy(alpha = if (c.isDark) 0.35f else 0.25f))
            GlassWeight.Thick -> c.glassFillStrong.compositeOver(c.background.copy(alpha = if (c.isDark) 0.55f else 0.45f))
        }
        HazeBlurStyle {
            blurRadius(
                when (weight) {
                    GlassWeight.Thin -> 18.dp
                    GlassWeight.Regular -> 28.dp
                    GlassWeight.Thick -> 40.dp
                },
            )
            noiseFactor(if (c.isDark) 0.06f else 0.04f)
            backgroundColor(c.background)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
            fallbackColorEffect(HazeColorEffect.tint(c.surface.copy(alpha = if (c.isDark) 0.92f else 0.94f)))
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha + bg.alpha * (1 - alpha)
    if (a == 0f) return Color.Transparent
    fun ch(f: Float, b: Float) = (f * alpha + b * bg.alpha * (1 - alpha)) / a
    return Color(ch(red, bg.red), ch(green, bg.green), ch(blue, bg.blue), a)
}

/**
 * Frosted glass background for chrome and floating surfaces: real blur of whatever lies beneath
 * (when supported), a soft inner sheen and a light-catching edge.
 */
@Composable
fun Modifier.glass(shape: Shape, weight: GlassWeight = GlassWeight.Regular, blur: Boolean = true, edge: Boolean = true): Modifier {
    val c = Aurum.colors
    val state = LocalGlassState.current
    var m = this.clip(shape)
    m = if (blur && state != null && Glass.blurSupported) {
        m.hazeBlur(HazeInput.Sources(state), blurStyle(weight))
    } else {
        // Frosted fallback: translucent fill over the ambient backdrop.
        val fill = when (weight) {
            GlassWeight.Thin -> c.glassFill
            GlassWeight.Regular -> if (c.isDark) Color(0xD9141416) else Color(0xE6FFFFFF)
            GlassWeight.Thick -> if (c.isDark) Color(0xF2141416) else Color(0xF5FFFFFF)
        }
        m.background(fill)
    }
    m = m.background(c.glassSheenBrush)
    if (edge) m = m.border(1.dp, c.glassStroke, shape)
    return m
}

/**
 * Light-weight glass material for cards inside scrolling content: translucent fill, sheen and edge light
 * over the ambient backdrop. No per-card blur, so long lists scroll smoothly on mid-range phones.
 */
@Composable
fun Modifier.glassCard(shape: Shape, highlighted: Boolean = false): Modifier {
    val c = Aurum.colors
    val stroke = if (highlighted) Brush.linearGradient(listOf(c.goldBright, c.gold.copy(alpha = 0.5f), c.goldDeep.copy(alpha = 0.7f))) else c.glassStroke
    return this
        .clip(shape)
        .background(c.glassFill)
        .background(c.glassSheenBrush)
        .border(if (highlighted) 1.25.dp else 1.dp, stroke, shape)
}

/**
 * The ambient backdrop behind every screen: a calm vertical wash with three soft light pools
 * (gold, cool, warm) that give the glass something to refract. Static by design — no per-frame cost.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Canvas(modifier.fillMaxSize().glassSource(GlassLayer.Backdrop)) {
        drawRect(Brush.verticalGradient(listOf(c.ambientTop, c.ambientBottom)))
        val w = size.width
        val h = size.height
        fun orb(color: Color, cx: Float, cy: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        orb(c.orbGold, w * 0.92f, h * 0.06f, w * 0.85f)
        orb(c.orbCool, w * 0.02f, h * 0.42f, w * 0.80f)
        orb(c.orbWarm, w * 0.85f, h * 0.88f, w * 0.75f)
    }
}
