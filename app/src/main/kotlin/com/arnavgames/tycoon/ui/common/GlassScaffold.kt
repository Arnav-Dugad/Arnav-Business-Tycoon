package com.arnavgames.tycoon.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassSource

/**
 * Pushed-screen layout: a large title at the top of the list that hands over to a compact frosted
 * navigation bar as you scroll, with content blurring beneath it.
 */
@Composable
fun GlassScaffold(title: String, onBack: () -> Unit, bottomBar: (@Composable () -> Unit)? = null, content: LazyListScope.() -> Unit) {
    val c = Aurum.colors
    val list = rememberLazyListState()
    var barPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The compact title fades in once the large title has scrolled away.
    val showCompactTitle by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 40 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content),
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = with(density) { barPx.toDp() }, bottom = navInset + Space.huge + with(density) { bottomPx.toDp() }),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "__large_title") {
                Text(title, style = Aurum.type.hero, color = c.textPrimary, modifier = Modifier.padding(start = Space.xs, top = Space.xs, bottom = Space.xs).semantics { heading() })
            }
            content()
        }
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).onSizeChanged { barPx = it.height }.glass(RectangleShape, GlassWeight.Regular, edge = false)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(Space.touch).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack).semantics { contentDescription = "Back" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(36.dp).glass(AurumShapes.pill, GlassWeight.Thin, blur = false), contentAlignment = Alignment.Center) {
                        AbtIcon(AbtIcons.ChevronLeft, null, tint = c.goldText, size = 20.dp)
                    }
                }
                Spacer(Modifier.width(Space.xs))
                Text(title, style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.weight(1f).alpha(if (showCompactTitle) 1f else 0f), maxLines = 1)
                Spacer(Modifier.width(Space.touch))
            }
            HairlineDivider(Modifier.alpha(if (showCompactTitle) 1f else 0f))
        }
        // The screen's one primary action, pinned to the bottom edge where it never moves (docs/UX.md).
        if (bottomBar != null) Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).onSizeChanged { bottomPx = it.height }.glass(RectangleShape, GlassWeight.Regular, edge = false)) {
            HairlineDivider()
            Box(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.sm, bottom = Space.sm + navInset)) { bottomBar() }
        }
    }
}

/** The round glass back button used by every pushed screen (48 dp touch target, "Back" for screen readers). */
@Composable
fun BackChip(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Box(
        modifier.size(Space.touch).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack).semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).glass(AurumShapes.pill, GlassWeight.Thin, blur = false), contentAlignment = Alignment.Center) {
            AbtIcon(AbtIcons.ChevronLeft, null, tint = c.goldText, size = 20.dp)
        }
    }
}
