package com.arnavgames.tycoon.ui.empire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.ui.EmpireUi
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.countdownText
import kotlin.math.roundToInt

private data class Earned(val id: Long, val at: Offset, val text: String)
private data class Ripple(val id: Long, val at: Offset)

/**
 * The counter, as the Empire tab's main surface: the whole panel is the tap area (no button to aim for). A tap sends a
 * ring out from the finger and the real amount earned (the engine's HustleEarned value) floats up from it. The price
 * per customer, a frenzy button and the combo meter sit at the panel's edges, out of the way.
 */
@Composable
fun TapStage(vm: GameViewModel, e: EmpireUi, fmt: (Money) -> String, guide: String?, modifier: Modifier = Modifier) {
    val h = e.hustle
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val density = LocalDensity.current

    val earned = remember { mutableStateListOf<Earned>() }
    val ripples = remember { mutableStateListOf<Ripple>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val flash = remember { Animatable(0f) }
    var flashAt by remember { mutableStateOf(Offset.Unspecified) }
    val pendingTaps = remember { ArrayDeque<Offset>() }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            if (ev is GameEvent.HustleEarned) {
                val at = pendingTaps.removeFirstOrNull() ?: Offset(stageSize.width / 2f, stageSize.height / 2f)
                earned.add(Earned(nextId++, at, "+" + fmt(ev.amount)))
                if (earned.size > 14) earned.removeAt(0)
            }
        }
    }
    fun tap(at: Offset) {
        pendingTaps.addLast(at)
        while (pendingTaps.size > 14) pendingTaps.removeFirst()
        if (!reduced) { flashAt = at; ripples.add(Ripple(nextId++, at)); if (ripples.size > 8) ripples.removeAt(0) }
        vm.dispatch(Command.HustleTap(1))
    }

    val glow = rememberInfiniteTransition(label = "stage")
    val breathe by glow.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "breathe")
    // Light backgrounds need a warmer glow to read as gold.
    val base = if (c.isDark) 0.2f else 0.3f
    val glowAlpha = (if (h.frenzyActive) base * 2f else base) * (if (reduced) 1f else breathe)
    // A brief brightening after each tap, centred where the finger landed.
    LaunchedEffect(nextId) { if (!reduced && nextId > 0) { flash.snapTo(1f); flash.animateTo(0f, tween(380)) } }

    Box(
        modifier
            .clip(AurumShapes.large)
            .glass(AurumShapes.large, GlassWeight.Thin, blur = false)
            .background(Brush.radialGradient(listOf(c.gold.copy(alpha = glowAlpha), Color.Transparent), radius = 900f))
            .border(1.dp, Brush.verticalGradient(listOf(c.goldBright.copy(alpha = if (h.frenzyActive) 0.9f else 0.45f), Color.Transparent)), AurumShapes.large)
            .onSizeChanged { stageSize = it }
            .pointerInput(Unit) { detectTapGestures(onTap = { at -> tap(at) }) }
            .semantics {
                role = Role.Button
                contentDescription = "Serve a customer, earns ${fmt(h.perTap)}"
                onClick(label = "Serve a customer") { tap(Offset(stageSize.width / 2f, stageSize.height / 2f)); true }
            }
            .testTag("counter-stage"),
    ) {
        // Decoration: faint rings around the centre, like a counter bell's reach, and a soft flash where you tap.
        Canvas(Modifier.matchParentSize()) {
            val centre = Offset(stageSize.width / 2f, stageSize.height / 2f)
            val ring = c.goldText.copy(alpha = if (c.isDark) 0.08f else 0.1f)
            for (i in 1..4) drawCircle(ring, radius = size.minDimension * 0.14f * i, center = centre, style = Stroke(width = 1.dp.toPx()))
            if (flash.value > 0f && flashAt != Offset.Unspecified) {
                drawCircle(Brush.radialGradient(listOf(c.goldBright.copy(alpha = 0.22f * flash.value), Color.Transparent), center = flashAt, radius = size.minDimension * 0.6f),
                    radius = size.minDimension * 0.6f, center = flashAt)
            }
        }
        // Watermark: what to do, quietly, in the middle.
        Column(Modifier.align(Alignment.Center).alpha(0.22f), horizontalAlignment = Alignment.CenterHorizontally) {
            AbtIcon(AbtIcons.Hand, null, tint = c.goldText, size = 64.dp)
            Spacer(Modifier.height(Space.xs))
            Text("Tap anywhere to serve customers", style = Aurum.type.titleSmall, color = c.textPrimary)
        }

        // Rings from the finger.
        ripples.forEach { r -> key(r.id) { RippleRing(r, onDone = { ripples.remove(r) }) } }

        // Top: what a customer pays (left), frenzy (right).
        Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("EACH CUSTOMER PAYS", style = Aurum.type.overline, color = c.textTertiary)
                Text(fmt(h.perTap), style = Aurum.type.headline, color = c.textPrimary)
                if (h.combo > 1 || h.frenzyActive) {
                    Text(
                        listOfNotNull(if (h.combo > 1) "Combo ×${"%.2f".format(h.comboMultiplier)}" else null, if (h.frenzyActive) "Frenzy ×3" else null).joinToString(" · "),
                        style = Aurum.type.label, color = c.goldText,
                    )
                }
            }
            FrenzyPill(h.frenzyActive, h.frenzyCharges, countdownText(h.frenzyEndsAtSim - h.simNow)) { vm.dispatch(Command.ActivateFrenzy) }
        }
        guide?.let { g ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 84.dp, start = Space.md, end = Space.md).fillMaxWidth()
                    .glass(AurumShapes.medium, GlassWeight.Regular, blur = false).padding(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 18.dp)
                Spacer(Modifier.width(Space.sm))
                Text(g, style = Aurum.type.bodySmall, color = c.textPrimary)
            }
        }

        // Bottom: the combo meter.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.md)) {
            GoldProgress(h.combo.toFloat() / h.maxCombo, height = 5.dp, label = "Combo ${h.combo} of ${h.maxCombo}")
            Spacer(Modifier.height(Space.xxs))
            Text(
                if (h.combo > 1) "Combo ${h.combo} of ${h.maxCombo}: keep tapping" else "Tap quickly to build a combo",
                style = Aurum.type.label, color = c.textTertiary,
            )
        }

        // The money, rising from where the finger was.
        earned.forEach { p -> key(p.id) { EarnedPop(p, reduced, density) { earned.remove(p) } } }
    }
}

@Composable
private fun FrenzyPill(active: Boolean, charges: Int, left: String, onStart: () -> Unit) {
    val c = Aurum.colors
    val on = active || charges > 0
    Row(
        Modifier.clip(AurumShapes.pill)
            .background(if (active) c.gold else Color.Transparent)
            .border(1.dp, if (on) c.gold else c.outline, AurumShapes.pill)
            .clickable(enabled = !active && charges > 0, role = Role.Button, onClickLabel = "Start a frenzy", onClick = onStart)
            .heightIn(min = 40.dp).padding(horizontal = Space.md, vertical = Space.xs)
            .testTag("frenzy"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(AbtIcons.Bolt, null, tint = if (active) c.textOnGold else if (on) c.goldText else c.textTertiary, size = 16.dp)
        Spacer(Modifier.width(Space.xs))
        Text(
            when { active -> "×3 · $left"; charges > 0 -> "Frenzy ×3 · $charges"; else -> "No frenzies" },
            style = Aurum.type.label, color = if (active) c.textOnGold else if (on) c.textPrimary else c.textTertiary,
        )
    }
}

@Composable
private fun BoxScope.RippleRing(r: Ripple, onDone: () -> Unit) {
    val c = Aurum.colors
    val t = remember { Animatable(0f) }
    LaunchedEffect(r.id) { t.animateTo(1f, tween(520, easing = FastOutSlowInEasing)); onDone() }
    Canvas(Modifier.matchParentSize()) {
        val radius = 18.dp.toPx() + 70.dp.toPx() * t.value
        drawCircle(c.goldBright.copy(alpha = 0.55f * (1f - t.value)), radius = radius, center = r.at, style = Stroke(width = 2.5.dp.toPx() * (1f - t.value * 0.6f)))
        drawCircle(c.gold.copy(alpha = 0.18f * (1f - t.value)), radius = radius * 0.55f, center = r.at)
    }
}

@Composable
private fun BoxScope.EarnedPop(p: Earned, reduced: Boolean, density: androidx.compose.ui.unit.Density, onDone: () -> Unit) {
    val c = Aurum.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(p.id) {
        anim.animateTo(1f, tween(if (reduced) 1 else 850))
        onDone()
    }
    val rise = with(density) { (64.dp * anim.value).toPx() }
    val drift = ((p.id % 5) - 2) * with(density) { 6.dp.toPx() }
    Text(
        p.text, style = Aurum.type.title, color = c.goldText,
        modifier = Modifier.align(Alignment.TopStart)
            .offset { IntOffset((p.at.x - 36.dp.toPx() + drift * anim.value).roundToInt(), (p.at.y - 44.dp.toPx() - rise).roundToInt()) }
            .alpha(1f - anim.value * anim.value),
    )
}
