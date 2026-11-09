package com.arnavgames.tycoon.ui.common

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass

/**
 * A UI choice remembered on this device (a sort order, a filter): read once, written whenever it changes. These are
 * conveniences, never game state, so they live in SharedPreferences and not in the save.
 */
@Composable
fun rememberPref(key: String, default: String): MutableState<String> {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    LaunchedEffect(key, state.value) { prefs.edit().putString(key, state.value).apply() }
    return state
}

/** One choice in a sort or filter row. */
data class Choice(val id: String, val label: String)

/**
 * A compact "Sort: Income" control: tap to move to the next order. One small target instead of a row of chips,
 * so lists keep their room; screen readers hear the current order and what a tap does.
 */
@Composable
fun SortToggle(choices: List<Choice>, current: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val i = choices.indexOfFirst { it.id == current }.coerceAtLeast(0)
    val now = choices[i]
    Row(
        modifier.clip(AurumShapes.pill)
            .clickable(role = Role.Button, onClickLabel = "Change the order") { onChange(choices[(i + 1) % choices.size].id) }
            .heightIn(min = Space.touch).padding(horizontal = Space.sm)
            .semantics(mergeDescendants = true) { contentDescription = "Sorted by ${now.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(AbtIcons.Sort, null, tint = c.goldText, size = 16.dp)
        Spacer(Modifier.width(Space.xxs + 2.dp))
        Text(now.label, style = Aurum.type.label, color = c.goldText, maxLines = 1)
    }
}

/** A frosted search field with a clear button. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val focus = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = { if (it.length <= 40) onQuery(it) },
        singleLine = true,
        textStyle = Aurum.type.body.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.gold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false).heightIn(min = Space.touch).padding(start = Space.md, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbtIcon(AbtIcons.Search, null, tint = c.textTertiary, size = 18.dp)
                Spacer(Modifier.width(Space.sm))
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, style = Aurum.type.body, color = c.textTertiary, maxLines = 1)
                    inner()
                }
                if (query.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.clip(AurumShapes.pill).clickable(role = Role.Button, onClickLabel = "Clear search") { onQuery(""); focus.clearFocus() }
                            .padding(Space.sm),
                    ) { AbtIcon(AbtIcons.Close, "Clear search", tint = c.textSecondary, size = 16.dp) }
                }
            }
        },
    )
}
