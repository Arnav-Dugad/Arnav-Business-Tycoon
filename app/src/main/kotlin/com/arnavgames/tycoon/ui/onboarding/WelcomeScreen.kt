package com.arnavgames.tycoon.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.CrownMark
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/** First-run welcome. One clear action: open the first business. */
@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900)) }
    Box(
        Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.xl))
            CrownMark(Modifier.alpha(reveal.value), size = 132.dp)
            Spacer(Modifier.height(Space.xxl))
            Text("ARNAV GAMES PRESENTS", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.alpha(reveal.value))
            Spacer(Modifier.height(Space.sm))
            Text(
                "Arnav Business Tycoon", style = Aurum.type.hero, color = c.textPrimary, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }.alpha(reveal.value),
            )
            Spacer(Modifier.height(Space.xl))
            Text(
                "Every fortune starts small. Yours starts with a folding table on a busy corner of Lisbon.",
                style = Aurum.type.body, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(Space.xxl))
            AurumCard(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                Promise(AbtIcons.Store, "Run real businesses", "Set prices, earn reputation, plan delivery routes.")
                Spacer(Modifier.height(Space.lg))
                Promise(AbtIcons.Manager, "Hire managers", "They keep the doors open while you're away.")
                Spacer(Modifier.height(Space.lg))
                Promise(AbtIcons.Crown, "Build a dynasty", "Climb chapter by chapter from kiosk to empire.")
            }
            Spacer(Modifier.height(Space.xxxl))
            GoldButton("Open your kiosk", onClick = onBegin, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp), leadingIcon = AbtIcons.Store)
            Spacer(Modifier.height(Space.md))
            Text("Free to play. Works fully offline.", style = Aurum.type.bodySmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    val c = Aurum.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        IconTile(icon, size = 44.dp)
        Spacer(Modifier.width(Space.md))
        Column {
            Text(title, style = Aurum.type.titleSmall, color = c.textPrimary)
            Text(body, style = Aurum.type.bodySmall, color = c.textSecondary)
        }
    }
}
