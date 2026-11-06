package com.arnavgames.tycoon.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass

/** Thin rounded progress bar. Gold by default; animates unless reduced motion is on. */
@Composable
fun GoldProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    brush: Brush? = null,
    track: Color = Aurum.colors.separator,
    label: String? = null,
) {
    val c = Aurum.colors
    val target = progress.coerceIn(0f, 1f)
    val p by animateFloatAsState(target, if (Aurum.reducedMotion) tween(0) else tween(450), label = "progress")
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
        if (p > 0f) drawRoundRect(brush = fill, size = Size((size.width * p).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/**
 * Segmented control: equal-width segments on a frosted track with a raised thumb that slides
 * to the selection with a spring.
 */
@Composable
fun <T> SegmentedSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Aurum.colors
    val index = options.indexOf(selected).coerceAtLeast(0)
    val reduced = Aurum.reducedMotion
    val animatedIndex by animateFloatAsState(index.toFloat(), if (reduced) tween(0) else spring(dampingRatio = 0.78f, stiffness = 520f), label = "seg")
    val thumbColor = if (c.isDark) Color(0xFF5A5A60) else Color.White
    Layout(
        modifier = modifier
            .clip(AurumShapes.small)
            .background(if (c.isDark) Color(0x2EFFFFFF) else Color(0x14000000))
            .padding(2.dp)
            .selectableGroup(),
        content = {
            // Thumb
            Box(
                Modifier
                    .shadow(if (c.isDark) 0.dp else 3.dp, AurumShapes.tiny, clip = false)
                    .clip(AurumShapes.tiny)
                    .background(thumbColor),
            )
            options.forEach { o ->
                val isSel = o == selected
                Box(
                    Modifier
                        .clip(AurumShapes.tiny)
                        .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(o) })
                        .heightIn(min = 34.dp)
                        .padding(horizontal = Space.md, vertical = Space.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), style = if (isSel) Aurum.type.titleSmall else Aurum.type.label, color = if (isSel) c.textPrimary else c.textSecondary, maxLines = 1)
                }
            }
        },
    ) { measurables, constraints ->
        val thumb = measurables.first()
        val segments = measurables.drop(1)
        val natural = segments.maxOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth / segments.size else natural
        val segW = if (constraints.hasFixedWidth) constraints.maxWidth / segments.size else minOf(natural, maxW)
        val placeables = segments.map { it.measure(Constraints.fixedWidth(segW)) }
        val h = placeables.maxOf { it.height }
        val thumbP = thumb.measure(Constraints.fixed(segW, h))
        layout(segW * segments.size, h) {
            thumbP.place((animatedIndex * segW).toInt(), 0)
            placeables.forEachIndexed { i, p -> p.place(i * segW, 0) }
        }
    }
}

/** Capsule toggle switch with a raised thumb (51×31). */
@Composable
fun AurumToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val track by animateColorAsState(if (checked) c.gold else if (c.isDark) Color(0x52787880) else Color(0x29787880), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, if (reduced) tween(0) else spring(dampingRatio = 0.7f, stiffness = 700f), label = "thumb")
    var m = modifier.size(width = 51.dp, height = 31.dp).clip(AurumShapes.pill).background(track)
    if (onCheckedChange != null) m = m.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    Box(m) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Small status chip with optional icon. */
@Composable
fun AurumChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Aurum.colors.textSecondary, background: Color? = null) {
    val c = Aurum.colors
    Row(
        modifier
            .clip(AurumShapes.pill)
            .background(background ?: tint.copy(alpha = if (c.isDark) 0.16f else 0.10f))
            .padding(horizontal = Space.sm + Space.xxs, vertical = Space.xxs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AbtIcon(icon, null, tint = tint, size = 13.dp)
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = Aurum.type.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** App-icon style tile with continuous corners: gold gradient when highlighted, frosted otherwise. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp, highlighted: Boolean = false, tint: Color = Aurum.colors.goldText) {
    val c = Aurum.colors
    val shape = com.arnavgames.tycoon.designsystem.theme.SmoothCornerShape(size * 0.28f)
    Box(
        modifier
            .size(size)
            .then(
                if (highlighted) Modifier.clip(shape).background(Brush.linearGradient(listOf(c.goldFillTop, c.goldFillBottom))).gloss(0.32f)
                else Modifier.glass(shape, GlassWeight.Thin, blur = false),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AbtIcon(icon, null, tint = if (highlighted) c.textOnGold else tint, size = size * 0.5f)
    }
}

data class NavItem(val key: String, val label: String, val icon: ImageVector, val badge: Boolean = false)

/**
 * Floating glass tab bar: a frosted capsule above the content with a softly sliding selection lens.
 * Labels scale with the user's font size but are capped so five tabs always fit; the full label is
 * exposed to screen readers.
 */
@Composable
fun AurumBottomBar(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val index = items.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val reduced = Aurum.reducedMotion
    val pos by animateFloatAsState(index.toFloat(), if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 420f), label = "lens")
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.lg, vertical = Space.sm),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(if (c.isDark) 0.dp else 16.dp, AurumShapes.pill, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
                .glass(AurumShapes.pill, GlassWeight.Thick),
        ) {
            val itemW = maxWidth / items.size
            // Selection lens
            Box(
                Modifier
                    .offset(x = itemW * pos + 4.dp)
                    .padding(vertical = 5.dp)
                    .width(itemW - 8.dp)
                    .fillMaxHeight()
                    .clip(AurumShapes.pill)
                    .background(c.selectionFill),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                items.forEach { item ->
                    val isSel = item.key == selected
                    val tint by animateColorAsState(if (isSel) c.goldText else c.textSecondary, label = "nav")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(AurumShapes.pill)
                            .selectable(selected = isSel, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onSelect(item.key) })
                            .semantics(mergeDescendants = true) { contentDescription = item.label + if (item.badge) ", has rewards" else "" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            AbtIcon(item.icon, null, tint = tint, size = 23.dp)
                            if (item.badge) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(8.dp).clip(CircleShape).background(c.loss))
                        }
                        Spacer(Modifier.height(3.dp))
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.25f))) {
                            Text(item.label, style = Aurum.type.label.copy(fontSize = Aurum.type.label.fontSize * 0.85f), color = tint, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** Height reserved at the bottom of scrolling content so it can scroll clear of the floating tab bar. */
val BottomBarReserve: Dp = 98.dp

/** Grouped-list row for settings and menus. */
@Composable
fun MenuRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = { AbtIcon(AbtIcons.ChevronRight, null, tint = Aurum.colors.textTertiary, size = 18.dp) },
) {
    val c = Aurum.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = Space.touch + Space.sm).clip(AurumShapes.small).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 32.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Aurum.type.title, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = Aurum.type.bodySmall, color = c.textSecondary)
        }
        trailing()
    }
}
