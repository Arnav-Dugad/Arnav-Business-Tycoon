package com.arnavgames.tycoon.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space

/** Renders one of [com.arnavgames.tycoon.designsystem.icons.AbtIcons]. Pass null description for decorative icons. */
@Composable
fun AbtIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Aurum.colors.textPrimary,
    size: Dp = 24.dp,
) {
    Icon(imageVector = icon, contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

/** The brand mark: an original crown inside a gold ring, drawn in code (scales without assets). */
@Composable
fun CrownMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val c = Aurum.colors
    Canvas(modifier.size(size).semantics { contentDescription = "Arnav Business Tycoon crest" }) {
        val w = this.size.minDimension
        val gold = Brush.linearGradient(listOf(c.goldBright, c.gold, c.goldDeep), start = Offset(0f, 0f), end = Offset(w, w))
        val ring = w * 0.035f
        drawCircle(brush = Brush.radialGradient(listOf(c.surfaceHigh, c.surface), radius = w / 2), radius = w / 2 - ring)
        drawCircle(brush = gold, radius = w / 2 - ring / 2, style = Stroke(width = ring))
        drawCircle(brush = gold, radius = w / 2 - ring * 3.2f, style = Stroke(width = ring * 0.35f), alpha = 0.55f)
        fun p(x: Float, y: Float) = Offset(w * x / 24f, w * y / 24f)
        val crown = Path().apply {
            val pts = listOf(p(6.0f, 9.4f), p(9.0f, 12.2f), p(12f, 6.6f), p(15.0f, 12.2f), p(18.0f, 9.4f), p(16.9f, 15.8f), p(7.1f, 15.8f))
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(crown, brush = gold)
        drawPath(crown, color = c.goldDeep, style = Stroke(width = w * 0.012f, join = StrokeJoin.Round))
        drawLine(brush = gold, start = p(7.1f, 17.6f), end = p(16.9f, 17.6f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        listOf(p(6.0f, 9.4f), p(12f, 6.6f), p(18.0f, 9.4f)).forEach { drawCircle(brush = gold, radius = w * 0.03f, center = it) }
    }
}

/** Press feedback shared by all tappable surfaces (respects reduced motion). */
@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val reduced = Aurum.reducedMotion
    val s by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    return s
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = Aurum.colors
    val source = remember { MutableInteractionSource() }
    val bg = if (enabled) Brush.horizontalGradient(listOf(c.goldBright, c.gold)) else Brush.horizontalGradient(listOf(c.surfaceHigh, c.surfaceHigh))
    Row(
        modifier
            .scale(pressScale(source))
            .defaultMinSize(minHeight = 52.dp)
            .clip(AurumShapes.pill)
            .background(bg)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xxl, vertical = Space.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.textOnGold else c.textTertiary
        if (leadingIcon != null) {
            AbtIcon(leadingIcon, null, tint = fg, size = 20.dp)
            Box(Modifier.width(Space.sm))
        }
        Text(text, style = Aurum.type.titleSmall, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = Aurum.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .scale(pressScale(source))
            .defaultMinSize(minHeight = 52.dp)
            .clip(AurumShapes.pill)
            .border(BorderStroke(1.dp, if (enabled) c.outlineStrong else c.outline), AurumShapes.pill)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xxl, vertical = Space.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) c.textPrimary else c.textTertiary
        if (leadingIcon != null) {
            AbtIcon(leadingIcon, null, tint = c.goldText, size = 20.dp)
            Box(Modifier.width(Space.sm))
        }
        Text(text, style = Aurum.type.titleSmall, color = fg)
    }
}

/** Standard elevated card: hairline border, no heavy shadows (luxury print feel). */
@Composable
fun AurumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aurum.colors
    val source = remember { MutableInteractionSource() }
    val border = if (highlighted) BorderStroke(1.dp, Brush.linearGradient(listOf(c.goldBright, c.goldDeep))) else BorderStroke(1.dp, c.outline)
    var m = modifier
        .scale(if (onClick != null) pressScale(source) else 1f)
        .clip(AurumShapes.medium)
        .background(c.surface)
        .border(border, AurumShapes.medium)
    if (onClick != null) m = m.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = Aurum.type.overline, color = Aurum.colors.textTertiary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Small pill showing an icon + value (cash, bullion, boosts). */
@Composable
fun StatPill(icon: ImageVector, value: String, description: String, modifier: Modifier = Modifier, tint: Color = Aurum.colors.goldText) {
    val c = Aurum.colors
    Row(
        modifier
            .clip(AurumShapes.pill)
            .background(c.surfaceRaised)
            .border(1.dp, c.outline, AurumShapes.pill)
            .padding(horizontal = Space.md, vertical = Space.xs + Space.xxs)
            .semantics(mergeDescendants = true) { contentDescription = "$description $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(icon, null, tint = tint, size = 16.dp)
        Box(Modifier.width(Space.xs + Space.xxs))
        Text(value, style = Aurum.type.numberSmall, color = c.textPrimary)
    }
}

/** Signed change label: always arrow + sign + color, so it never relies on color alone. */
@Composable
fun ChangeLabel(percent: Double, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val up = percent >= 0
    val color = if (up) c.gain else c.loss
    val text = (if (up) "+" else "−") + String.format(java.util.Locale.US, "%.2f%%", kotlin.math.abs(percent))
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = (if (up) "up " else "down ") + text }, verticalAlignment = Alignment.CenterVertically) {
        AbtIcon(if (up) com.arnavgames.tycoon.designsystem.icons.AbtIcons.ArrowUp else com.arnavgames.tycoon.designsystem.icons.AbtIcons.ArrowDown, null, tint = color, size = 14.dp)
        Box(Modifier.width(Space.xxs))
        Text(text, style = Aurum.type.numberSmall, color = color)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Aurum.colors.outline))
}
