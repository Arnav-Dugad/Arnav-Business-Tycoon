package com.arnavgames.tycoon.ui.empire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.BackChip
import com.arnavgames.tycoon.ui.common.countdownText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class TapPop(val id: Long, val at: Offset, val text: String)

/**
 * The counter, full screen: tap anywhere on the stage to serve a customer. Each tap pays what the engine says (the
 * floating amount is the real HustleEarned value), quick taps build a combo, and a frenzy triples everything for a while.
 */
@Composable
fun CounterScreen(vm: GameViewModel, onBack: () -> Unit) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val e = ui ?: return
    val h = e.hustle
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val pops = remember { mutableStateListOf<TapPop>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var stage by remember { mutableStateOf(IntSize.Zero) }
    val pendingTaps = remember { ArrayDeque<Offset>() }
    var sessionEarned by remember { mutableStateOf(Money.ZERO) }
    var sessionTaps by remember { mutableLongStateOf(0L) }
    val press = remember { Animatable(1f) }

    // Each real earning floats up from where the finger was.
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            if (ev is GameEvent.HustleEarned) {
                val at = pendingTaps.removeFirstOrNull() ?: Offset(stage.width / 2f, stage.height / 2f)
                sessionEarned += ev.amount
                pops.add(TapPop(nextId++, at, "+" + fmt(ev.amount)))
                if (pops.size > 12) pops.removeAt(0)
            }
        }
    }
    fun tap(at: Offset) {
        pendingTaps.addLast(at)
        while (pendingTaps.size > 12) pendingTaps.removeFirst()
        sessionTaps++
        vm.dispatch(Command.HustleTap(1))
        if (!reduced) scope.launch { press.snapTo(0.92f); press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }
    }

    val glow = rememberInfiniteTransition(label = "glow")
    val pulse by glow.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")

    Box(
        Modifier.fillMaxSize().background(c.background)
            .background(Brush.radialGradient(listOf(c.gold.copy(alpha = if (h.frenzyActive) 0.32f else 0.16f), Color.Transparent), radius = 1100f)),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar: back, title, cash.
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                BackChip(onBack)
                Text("The Counter", style = Aurum.type.title, color = c.textPrimary, modifier = Modifier.weight(1f).padding(start = Space.xs).semantics { heading() })
                Row(
                    Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false).padding(horizontal = Space.md, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AbtIcon(AbtIcons.Cash, null, tint = c.goldText, size = 16.dp)
                    Spacer(Modifier.width(Space.xs))
                    RollingMoney(e.header.cash, e.header.income, style = Aurum.type.numberSmall, color = c.textPrimary, format = fmt)
                }
            }
            guideText(e, GuideWhere.Counter)?.let { g -> Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) { GuideBanner(g) } }

            // What a customer pays right now.
            Column(Modifier.fillMaxWidth().padding(top = Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EACH CUSTOMER PAYS", style = Aurum.type.overline, color = c.textTertiary)
                Text(fmt(h.perTap), style = Aurum.type.numberHero, color = c.textPrimary)
                if (h.combo > 1 || h.frenzyActive) {
                    Text(
                        listOfNotNull(
                            if (h.combo > 1) "Combo ×${"%.2f".format(h.comboMultiplier)}" else null,
                            if (h.frenzyActive) "Frenzy ×3" else null,
                        ).joinToString(" · "),
                        style = Aurum.type.titleSmall, color = c.goldText,
                    )
                }
            }

            // The stage: tap anywhere.
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onSizeChanged { stage = it }
                    .pointerInput(Unit) { detectTapGestures(onTap = { at -> tap(at) }) }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Serve a customer, earns ${fmt(h.perTap)}"
                        onClick(label = "Serve a customer") { tap(Offset(stage.width / 2f, stage.height / 2f)); true }
                    }
                    .testTag("counter-stage"),
                contentAlignment = Alignment.Center,
            ) {
                // Halo (stronger during a frenzy).
                Box(
                    Modifier.size(280.dp).alpha(if (reduced) 0.6f else pulse * (if (h.frenzyActive) 1f else 0.7f)).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(c.gold.copy(alpha = 0.35f), Color.Transparent))),
                )
                Box(
                    Modifier.size(208.dp).scale(press.value).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(c.goldFillTop, c.goldFillBottom, c.goldDeep)))
                        .border(2.dp, Brush.verticalGradient(listOf(c.goldBright, c.goldDeep)), CircleShape)
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = size.height * 0.5f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AbtIcon(AbtIcons.Hand, null, tint = c.textOnGold, size = 84.dp)
                }
                Text(
                    "Tap anywhere", style = Aurum.type.label, color = c.textTertiary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.sm),
                )
                pops.forEach { p ->
                    key(p.id) {
                        CounterPop(p, reduced, density) { pops.remove(p) }
                    }
                }
            }

            // Combo, frenzy and the session tally.
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                GoldProgress(h.combo.toFloat() / h.maxCombo, height = 6.dp, label = "Combo ${h.combo} of ${h.maxCombo}")
                Text(
                    if (h.combo > 1) "Combo ${h.combo} of ${h.maxCombo}: keep tapping to keep it" else "Tap quickly to build a combo: each customer pays more",
                    style = Aurum.type.label, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                if (h.frenzyActive) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        AurumChip("Frenzy ×3 · ${countdownText(h.frenzyEndsAtSim - h.simNow)} left", icon = AbtIcons.Bolt, tint = c.textOnGold, background = c.gold)
                    }
                } else {
                    GoldButton(
                        if (h.frenzyCharges > 0) "Start a frenzy ×3 · ${h.frenzyCharges} left" else "No frenzies left for now",
                        onClick = { vm.dispatch(Command.ActivateFrenzy) }, enabled = h.frenzyCharges > 0,
                        leadingIcon = AbtIcons.Bolt, modifier = Modifier.fillMaxWidth().testTag("frenzy"),
                    )
                }
                Text(
                    if (sessionTaps > 0) "This visit: $sessionTaps ${if (sessionTaps == 1L) "customer" else "customers"} · ${fmt(sessionEarned)}"
                    else "Customers served so far: ${"%,d".format(h.totalTaps)}",
                    style = Aurum.type.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CounterPop(p: TapPop, reduced: Boolean, density: androidx.compose.ui.unit.Density, onDone: () -> Unit) {
    val c = Aurum.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(p.id) {
        anim.animateTo(1f, tween(if (reduced) 1 else 850))
        onDone()
    }
    val rise = with(density) { (70.dp * anim.value).toPx() }
    val drift = ((p.id % 5) - 2) * with(density) { 6.dp.toPx() }
    Text(
        p.text, style = Aurum.type.title, color = c.goldText,
        modifier = Modifier.align(Alignment.TopStart)
            .offset { IntOffset((p.at.x - 40.dp.toPx() + drift * anim.value).roundToInt(), (p.at.y - 36.dp.toPx() - rise).roundToInt()) }
            .alpha(1f - anim.value * anim.value),
    )
}
