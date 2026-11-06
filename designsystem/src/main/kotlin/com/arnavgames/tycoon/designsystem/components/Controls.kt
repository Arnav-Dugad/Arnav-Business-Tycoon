package com.arnavgames.tycoon.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space

/** Thin rounded progress bar. Gold by default; animates unless reduced motion is on. */
@Composable
fun GoldProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    brush: Brush? = null,
    track: Color = Aurum.colors.surfaceHigh,
    label: String? = null,
) {
    val c = Aurum.colors
    val target = progress.coerceIn(0f, 1f)
    val p by animateFloatAsState(target, if (Aurum.reducedMotion) tween(0) else tween(400), label = "progress")
    val fill = brush ?: Brush.horizontalGradient(listOf(c.goldDeep, c.gold, c.goldBright))
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                if (label != null) contentDescription = label
            },
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = r)
        if (p > 0f) drawRoundRect(brush = fill, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

/** Segmented single-choice selector (e.g. buy quantity ×1 / ×10 / Max). */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Aurum.colors
    Row(
        modifier
            .clip(AurumShapes.pill)
            .background(c.surfaceHigh)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEach { o ->
            val isSel = o == selected
            val bg by animateColorAsState(if (isSel) c.surfaceRaised else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .clip(AurumShapes.pill)
                    .background(bg)
                    .then(if (isSel) Modifier.border(1.dp, c.outline, AurumShapes.pill) else Modifier)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(o) })
                    .heightIn(min = 36.dp)
                    .padding(horizontal = Space.md, vertical = Space.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = Aurum.type.label, color = if (isSel) c.textPrimary else c.textSecondary)
            }
        }
    }
}

/** Small status chip with optional icon. */
@Composable
fun AurumChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Aurum.colors.textSecondary, background: Color = Aurum.colors.surfaceHigh) {
    Row(
        modifier.clip(AurumShapes.pill).background(background).padding(horizontal = Space.sm + Space.xxs, vertical = Space.xxs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AbtIcon(icon, null, tint = tint, size = 13.dp)
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = Aurum.type.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Rounded square tile holding an icon — used for business and feature avatars. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp, highlighted: Boolean = false, tint: Color = Aurum.colors.goldText) {
    val c = Aurum.colors
    Box(
        modifier
            .size(size)
            .clip(AurumShapes.small)
            .background(if (highlighted) Brush.linearGradient(listOf(c.goldBright, c.gold, c.goldDeep)) else Brush.linearGradient(listOf(c.surfaceHigh, c.surfaceRaised)))
            .border(1.dp, if (highlighted) c.goldDeep else c.outline, AurumShapes.small),
        contentAlignment = Alignment.Center,
    ) {
        AbtIcon(icon, null, tint = if (highlighted) c.textOnGold else tint, size = size * 0.5f)
    }
}

data class NavItem(val key: String, val label: String, val icon: ImageVector, val badge: Boolean = false)

/** Bottom navigation bar with the custom icon set and a gold active indicator. */
@Composable
fun AurumBottomBar(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.xs, vertical = Space.xs).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val isSel = item.key == selected
                val tint by animateColorAsState(if (isSel) c.goldText else c.textTertiary, label = "nav")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(AurumShapes.medium)
                        .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(item.key) })
                        .semantics(mergeDescendants = true) { contentDescription = item.label + if (item.badge) ", has rewards" else "" }
                        .padding(vertical = Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AbtIcon(item.icon, null, tint = tint, size = 24.dp)
                        if (item.badge) {
                            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(AurumShapes.pill).background(c.loss))
                        }
                    }
                    Spacer(Modifier.height(Space.xxs + 1.dp))
                    // Tab labels scale with the user's font size but are capped so five tabs always fit;
                    // the full label is still exposed to screen readers via the tab's semantics.
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
                        Text(item.label, style = Aurum.type.label, color = tint, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(Space.xxs))
                    Box(Modifier.width(18.dp).height(2.dp).clip(AurumShapes.pill).background(if (isSel) c.gold else Color.Transparent))
                }
            }
        }
    }
}

/** Large tappable list row for settings and menus. */
@Composable
fun MenuRow(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = { AbtIcon(com.arnavgames.tycoon.designsystem.icons.AbtIcons.ChevronRight, null, tint = Aurum.colors.textTertiary, size = 20.dp) }) {
    val c = Aurum.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = Space.touch + Space.sm).clip(AurumShapes.small).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(icon, null, tint = c.goldText, size = 22.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Aurum.type.titleSmall, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = Aurum.type.bodySmall, color = c.textSecondary)
        }
        trailing()
    }
}
