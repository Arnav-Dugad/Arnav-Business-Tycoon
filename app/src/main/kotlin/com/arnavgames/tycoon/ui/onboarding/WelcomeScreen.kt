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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
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

/**
 * First-run welcome: the painting of the folding table in Lisbon fills the screen, the title and one line sit over its
 * fade, and there is one thing to do.
 */
@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1100)) }
    Box(Modifier.fillMaxSize().background(c.background)) {
        // The painting, with a slow settle as it appears.
        com.arnavgames.tycoon.ui.common.Illustration(
            com.arnavgames.tycoon.ui.common.Art.WELCOME,
            Modifier.fillMaxWidth().fillMaxHeight(0.72f).alpha(reveal.value)
                .graphicsLayer { val k = 1.06f - 0.06f * reveal.value; scaleX = k; scaleY = k },
        )
        // Fade the painting into the page so the text sits on calm ground.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.7f to c.background)))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = Space.gutter, vertical = Space.xl),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "Arnav Business Tycoon", style = Aurum.type.hero, color = c.textPrimary,
                modifier = Modifier.semantics { heading() }.alpha(reveal.value),
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "Every fortune starts small. Yours is a folding table on a corner in Lisbon.",
                style = Aurum.type.body, color = c.textSecondary, modifier = Modifier.widthIn(max = 420.dp).alpha(reveal.value),
            )
            Spacer(Modifier.height(Space.xl))
            GoldButton("Open your kiosk", onClick = onBegin, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp))
            Spacer(Modifier.height(Space.sm))
            Text("Free. Plays offline.", style = Aurum.type.label, color = c.textTertiary)
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
