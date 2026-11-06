package com.arnavgames.tycoon.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass

/**
 * Glass alert/sheet drawn inside the app window (not a separate dialog window), so it can blur the
 * live screen behind it. Place it at the root of the screen hierarchy, outside the glass sources.
 */
@Composable
fun AurumDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    BackHandler(enabled = dismissible, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim.copy(alpha = if (c.isDark) 0.45f else 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = dismissible, onClick = onDismiss)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.9f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.95f),
        ) {
            Column(
                Modifier
                    .padding(Space.gutter)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .shadow(if (c.isDark) 0.dp else 28.dp, AurumShapes.sheet, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
                    .glass(AurumShapes.sheet, GlassWeight.Thick)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow taps */ }
                    .padding(Space.xxl)
                    .semantics { paneTitle = title },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(title, style = Aurum.type.headline, color = c.textPrimary, textAlign = TextAlign.Center)
                content()
            }
        }
    }
}
