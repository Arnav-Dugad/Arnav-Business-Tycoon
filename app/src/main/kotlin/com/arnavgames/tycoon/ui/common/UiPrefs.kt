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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
 * The sort control: a labelled button ("Sort · Income ▾") that opens a menu of every order, the current one ticked.
 * Every option is visible at once (a cycling toggle hid them), each row is a full 48 dp target, and the choice is
 * remembered by the caller (see [rememberPref]).
 */
@Composable
fun SortToggle(choices: List<Choice>, current: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    val now = choices.firstOrNull { it.id == current } ?: choices.first()
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        Row(
            Modifier.clip(AurumShapes.pill).glass(AurumShapes.pill, GlassWeight.Thin, blur = false)
                .clickable(role = Role.Button, onClickLabel = "Choose the order") { open = true }
                .heightIn(min = 40.dp).padding(horizontal = Space.md)
                .semantics(mergeDescendants = true) { contentDescription = "Sorted by ${now.label}. Double tap to change." },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbtIcon(AbtIcons.Sort, null, tint = c.goldText, size = 15.dp)
            Spacer(Modifier.width(Space.xs))
            Text(now.label, style = Aurum.type.label, color = c.textPrimary, maxLines = 1)
            Spacer(Modifier.width(Space.xxs))
            AbtIcon(AbtIcons.ChevronDown, null, tint = c.textSecondary, size = 14.dp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            containerColor = c.surfaceHigh, shape = AurumShapes.medium,
        ) {
            Text("Sort by", style = Aurum.type.overline, color = c.textTertiary, modifier = Modifier.padding(start = Space.md, top = Space.xs, bottom = Space.xxs))
            choices.forEach { ch ->
                val selected = ch.id == now.id
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(ch.label, style = Aurum.type.body, color = if (selected) c.goldText else c.textPrimary) },
                    trailingIcon = { if (selected) AbtIcon(AbtIcons.Check, "Selected", tint = c.goldText, size = 18.dp) },
                    onClick = { open = false; onChange(ch.id) },
                    modifier = Modifier.heightIn(min = Space.touch),
                )
            }
        }
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

/** A money or quantity field: a large number pad entry with an optional "$" in front and a clear button. */
@Composable
fun AmountField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, prefix: String? = null) {
    val c = Aurum.colors
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 24 && v.all { it.isDigit() || it in ".,kKmMbBtTeE" }) onValue(v) },
        singleLine = true,
        textStyle = Aurum.type.number.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.gold),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder }.testTag("amount-field"),
        decorationBox = { inner ->
            Row(
                Modifier.glass(AurumShapes.medium, GlassWeight.Thin, blur = false).heightIn(min = 52.dp).padding(start = Space.md, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix != null) { Text(prefix, style = Aurum.type.number, color = c.textSecondary); Spacer(Modifier.width(Space.xxs)) }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = Aurum.type.body, color = c.textTertiary, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.clip(AurumShapes.pill).clickable(role = Role.Button, onClickLabel = "Clear") { onValue("") }.padding(Space.sm),
                    ) { AbtIcon(AbtIcons.Close, "Clear", tint = c.textSecondary, size = 16.dp) }
                }
            }
        },
    )
}

