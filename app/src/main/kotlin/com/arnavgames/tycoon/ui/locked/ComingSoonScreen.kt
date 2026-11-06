package com.arnavgames.tycoon.ui.locked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/** Helpful empty state for a tab whose system unlocks later in the story. */
@Composable
fun ComingSoonScreen(icon: ImageVector, title: String, chapter: Int, lines: List<String>) {
    val c = Aurum.colors
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.surfaceRaised, c.background)))
            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.huge))
        IconTile(icon, size = 88.dp, highlighted = true)
        Spacer(Modifier.height(Space.xl))
        Text(title, style = Aurum.type.display, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.sm))
        AurumChip("Unlocks in Chapter $chapter", icon = AbtIcons.Lock, tint = c.goldText)
        Spacer(Modifier.height(Space.xl))
        Text(
            "Keep growing your businesses and completing chapter goals. This part of your empire opens up as your story unfolds.",
            style = Aurum.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp),
        )
        Spacer(Modifier.height(Space.xl))
        AurumCard(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
            Text("WHAT AWAITS", style = Aurum.type.overline, color = c.textTertiary)
            Spacer(Modifier.height(Space.sm))
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 16.dp)
                        Spacer(Modifier.width(Space.md))
                        Text(line, style = Aurum.type.body, color = c.textPrimary)
                    }
                }
            }
        }
    }
}
