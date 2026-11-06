package com.arnavgames.tycoon.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.core.engine.mechanics.MechanicView
import com.arnavgames.tycoon.core.engine.mechanics.Tone
import com.arnavgames.tycoon.core.engine.mechanics.ViewControl
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass

/**
 * Renders any module mechanic from its [MechanicView] (engine-described UI), so new business mechanics need no
 * bespoke screen code. [onAction] sends (actionId, value) to the engine; the engine validates and charges costs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ModulePanel(
    view: MechanicView,
    cash: Money,
    managed: Boolean,
    freeActions: Set<String>,
    fmt: (Money) -> String,
    onAction: (String, String) -> Unit,
) {
    val c = Aurum.colors
    Text(view.summary, style = Aurum.type.body, color = c.textSecondary)
    Spacer(Modifier.height(Space.md))
    // Stats grid
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md), maxItemsInEachRow = 2) {
        view.stats.forEach { st ->
            Column(Modifier.weight(1f).widthIn(min = 120.dp).semantics(mergeDescendants = true) {}) {
                Text(st.label.uppercase(), style = Aurum.type.overline, color = c.textTertiary)
                Spacer(Modifier.height(Space.xxs))
                Text(st.value, style = Aurum.type.titleSmall, color = toneColor(st.tone))
            }
        }
    }
    view.controls.forEach { ctrl ->
        Spacer(Modifier.height(Space.lg))
        HairlineDivider()
        Spacer(Modifier.height(Space.md))
        val lockedByManager = managed && when (ctrl) {
            is ViewControl.Choice -> ctrl.actionId !in freeActions
            is ViewControl.Action -> ctrl.actionId !in freeActions
            is ViewControl.Progress -> false
        }
        when (ctrl) {
            is ViewControl.Progress -> {
                Text(ctrl.title, style = Aurum.type.title, color = c.textPrimary)
                Spacer(Modifier.height(Space.sm))
                GoldProgress(ctrl.fraction.toFloat(), label = "${ctrl.title}: ${ctrl.caption}")
                Spacer(Modifier.height(Space.xs))
                Text(ctrl.caption, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
            is ViewControl.Action -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ctrl.title, style = Aurum.type.title, color = c.textPrimary)
                        Text(ctrl.help, style = Aurum.type.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.width(Space.sm))
                    val affordable = ctrl.cost == null || ctrl.cost!! <= cash
                    PillAction(
                        text = if (ctrl.cost != null) "${ctrl.label} · ${fmt(ctrl.cost!!)}" else ctrl.label,
                        enabled = ctrl.enabled && affordable && !lockedByManager,
                        onClick = { onAction(ctrl.actionId, ctrl.value) },
                    )
                }
            }
            is ViewControl.Choice -> {
                Text(ctrl.title, style = Aurum.type.title, color = c.textPrimary)
                Text(if (lockedByManager) "Your manager decides this." else ctrl.help, style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.sm))
                val enabled = ctrl.enabled && !lockedByManager
                if (ctrl.options.size > 4) {
                    // Long scales (fares, prices) as a compact three-column grid instead of a tall list.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                        maxItemsInEachRow = 3,
                    ) {
                        ctrl.options.forEach { o ->
                            OptionTile(o.label, o.detail, o.id == ctrl.selected, enabled, compact = true, Modifier.weight(1f)) { onAction(ctrl.actionId, o.id) }
                        }
                        repeat((3 - ctrl.options.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
                    }
                } else {
                    ctrl.options.forEach { o ->
                        OptionTile(o.label, o.detail, o.id == ctrl.selected, enabled, compact = false, Modifier.fillMaxWidth().padding(vertical = Space.xxs)) { onAction(ctrl.actionId, o.id) }
                    }
                }
            }
        }
    }
}

/** One selectable option: a gold-outlined tile when selected, thin glass otherwise. Compact tiles stack text centered. */
@Composable
private fun OptionTile(label: String, detail: String, sel: Boolean, enabled: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Aurum.colors
    val shape = AurumShapes.small
    val m = modifier
        .then(
            if (sel) Modifier.clip(shape).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.10f)).border(1.25.dp, c.gold, shape)
            else Modifier.glass(shape, GlassWeight.Thin, blur = false),
        )
        .clickable(enabled = enabled && !sel, role = Role.RadioButton, onClick = onClick)
        .semantics(mergeDescendants = true) { stateDescription = if (sel) "Selected" else "Not selected" }
        .heightIn(min = Space.touch)
    val labelColor = if (enabled || sel) c.textPrimary else c.textTertiary
    if (compact) {
        Column(m.padding(horizontal = Space.xs, vertical = Space.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = Aurum.type.titleSmall, color = if (sel) c.goldText else labelColor, maxLines = 2, textAlign = TextAlign.Center)
            if (detail.isNotBlank()) Text(detail, style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 2, textAlign = TextAlign.Center)
        }
    } else {
        Row(m.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(if (sel) AbtIcons.Check else AbtIcons.Sparkle, null, tint = if (sel) c.goldText else c.textTertiary, size = 16.dp)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(label, style = Aurum.type.titleSmall, color = labelColor)
                if (detail.isNotBlank()) Text(detail, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun toneColor(t: Tone): Color = when (t) {
    Tone.Good -> Aurum.colors.gain
    Tone.Bad -> Aurum.colors.loss
    Tone.Warn -> Aurum.colors.warning
    Tone.Neutral -> Aurum.colors.textPrimary
}
