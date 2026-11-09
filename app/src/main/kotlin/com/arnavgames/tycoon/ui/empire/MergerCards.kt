package com.arnavgames.tycoon.ui.empire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.ui.HoldingUi
import com.arnavgames.tycoon.ui.MergerUi

/** One node of the merger tree: members with tier progress, the synergy, and the fee or what is blocking it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergerCard(m: MergerUi, fmt: (Money) -> String, onForm: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), highlighted = m.formed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The member businesses' paintings, fanned like cards.
            Box(Modifier.size(width = (40 + 22 * (m.members.size - 1).coerceAtLeast(0)).dp, height = 48.dp)) {
                m.members.forEachIndexed { i, mem ->
                    com.arnavgames.tycoon.ui.common.Illustration(
                        com.arnavgames.tycoon.ui.common.Art.business(mem.id),
                        Modifier.offset(x = (22 * i).dp).size(40.dp, 48.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small)
                            .border(1.5.dp, c.surface, com.arnavgames.tycoon.designsystem.theme.AurumShapes.small),
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = Aurum.type.title, color = c.textPrimary)
                Text(m.description, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
            if (m.formed) AurumChip("Formed", icon = AbtIcons.Check, tint = c.gain)
        }
        Spacer(Modifier.height(Space.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            m.members.forEach { mem ->
                AurumChip(
                    "${mem.name} · ${mem.note}",
                    icon = if (mem.ready) AbtIcons.Check else AbtIcons.Lock,
                    tint = if (mem.ready) c.gain else c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 14.dp)
            Spacer(Modifier.width(Space.xs))
            Text(m.synergy, style = Aurum.type.bodySmall, color = c.textPrimary)
        }
        if (!m.formed) {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.blocker ?: "Each business can join only one conglomerate.", style = Aurum.type.bodySmall, color = if (m.blocker != null) c.textTertiary else c.textSecondary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Space.sm))
                PillAction("Form · ${fmt(m.fee)}", enabled = m.blocker == null && m.canAfford, onClick = onForm)
            }
        }
    }
}

/** The holding company that crowns three conglomerates (unlocks the IPO later). */
@Composable
fun HoldingCard(h: HoldingUi, fmt: (Money) -> String, onForm: (String) -> Unit) {
    val c = Aurum.colors
    var name by rememberSaveable { mutableStateOf("") }
    AurumCard(Modifier.fillMaxWidth(), highlighted = h.name != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.Crown, highlighted = h.name != null)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(h.name ?: "Holding company", style = Aurum.type.title, color = c.textPrimary)
                Text(
                    if (h.name != null) "Every business earns ×1.25. The IPO arrives with the Dynasty update."
                    else "Merge three conglomerates under one name: every business earns ×1.25. (${h.conglomerates}/3 formed)",
                    style = Aurum.type.bodySmall, color = c.textSecondary,
                )
            }
        }
        if (h.name == null) {
            Spacer(Modifier.height(Space.md))
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 32) name = it },
                singleLine = true,
                enabled = h.unlocked,
                textStyle = Aurum.type.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Holding company name" },
                decorationBox = { inner ->
                    Row(Modifier.fillMaxWidth().glass(AurumShapes.small, GlassWeight.Thin, blur = false).padding(horizontal = Space.md, vertical = Space.md)) {
                        if (name.isEmpty()) Text("Name your holding company", style = Aurum.type.body, color = c.textTertiary)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(Space.sm))
            PillAction(
                "Form holding · ${fmt(h.fee)}",
                enabled = h.unlocked && h.canAfford && name.trim().length >= 3,
                onClick = { onForm(name.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
