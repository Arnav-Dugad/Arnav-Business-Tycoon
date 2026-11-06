package com.arnavgames.tycoon.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space

/** Premium modal card used for offline earnings, confirmations and milestone moments. */
@Composable
fun AurumDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aurum.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(Space.gutter)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(AurumShapes.large)
                .background(c.surface)
                .border(1.dp, Brush.linearGradient(listOf(c.goldBright, c.goldDeep)), AurumShapes.large)
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
