package com.arnavgames.tycoon.ui.counter

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.countdownText
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

private data class Pop(val id: Long, val at: Offset, val text: String)

/** A touch the shader ripples from: position, when it happened (seconds of shader time). */
private data class Touch(val at: Offset, val t: Float)

/**
 * The Counter tab: the whole screen is the counter. Every finger that lands serves a customer (true multi-touch);
 * coins burst from each one under real physics and are pulled into your cash, which pulses as they land; a GPU shader
 * lights the background in gold that ripples from each touch and warms with the combo. Nothing else lives here.
 */
@Composable
fun CounterScreen(vm: GameViewModel, guide: String? = null) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val e = ui ?: return
    val h = e.hustle
    // The touch handler is installed once; it reads the live combo and frenzy through this.
    val live by androidx.compose.runtime.rememberUpdatedState(h)
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    val c = Aurum.colors
    val reduced = Aurum.reducedMotion
    val density = LocalDensity.current
    val scale = density.density
    val scope = rememberCoroutineScope()
    val rnd = remember { Random(System.nanoTime()) }

    val coins = remember { CoinField() }
    val pops = remember { mutableStateListOf<Pop>() }
    val touches = remember { ArrayDeque<Touch>() }
    val pendingTaps = remember { ArrayDeque<Offset>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableLongStateOf(0L) }
    var time by remember { mutableFloatStateOf(0f) }
    var root by remember { mutableStateOf(Offset.Zero) }
    var cashTarget by remember { mutableStateOf(Offset(0f, 0f)) }
    var stage by remember { mutableStateOf(Size.Zero) }
    val pulse = remember { Animatable(1f) }
    // The GPU shader where the platform has it (not on the JVM test renderer).
    val shader = remember { if (GoldShader.supported && Build.FINGERPRINT != "robolectric") GoldShader.create() else null }
    // If the GPU path ever fails on a phone, fall back to the gradient for good instead of crashing.
    val shaderBroken = remember { booleanArrayOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            if (ev is GameEvent.HustleEarned) {
                val at = pendingTaps.removeFirstOrNull() ?: Offset(stage.width / 2f, stage.height / 2f)
                pops.add(Pop(nextId++, at, "+" + fmt(ev.amount)))
                if (pops.size > 16) pops.removeAt(0)
            }
        }
    }
    // One frame loop drives the physics, the shader clock and the pulse.
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 20f)
                last = now
                time += dt
                val arrived = coins.step(dt, cashTarget.x, cashTarget.y, scale)
                if (arrived > 0) scope.launch { pulse.snapTo(1.12f); pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
                while (touches.isNotEmpty() && time - touches.first().t > 1.2f) touches.removeFirst()
                frame++
            }
        }
    }
    fun serve(at: Offset) {
        pendingTaps.addLast(at)
        while (pendingTaps.size > 16) pendingTaps.removeFirst()
        if (!reduced) {
            val power = 1f + live.combo.toFloat() / live.maxCombo * 0.6f + if (live.frenzyActive) 0.5f else 0f
            coins.burst(at.x, at.y, if (live.frenzyActive) 9 else 5 + live.combo / 6, power, scale, rnd)
            touches.addLast(Touch(at, time))
            while (touches.size > 4) touches.removeFirst()
        }
        vm.dispatch(Command.HustleTap(1))
    }

    val heat = h.combo.toFloat() / h.maxCombo
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier.fillMaxSize().background(c.background)
            .onGloballyPositioned { root = it.positionInRoot(); stage = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
            // Every finger counts: a new pointer going down anywhere is a customer served.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { ch -> if (ch.changedToDown()) { serve(ch.position); ch.consume() } }
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Serve a customer, earns ${fmt(h.perTap)}"
                onClick(label = "Serve a customer") { serve(Offset(stage.width / 2f, stage.height / 2f)); true }
            }
            .testTag("counter-stage"),
    ) {
        // Your best business, painted faintly into the floor of the stage: the shop you are serving in.
        e.businesses.maxWithOrNull(compareBy<com.arnavgames.tycoon.ui.BusinessUi>({ it.tiersReached }, { it.level }))?.let { top ->
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.66f).align(Alignment.BottomCenter).alpha(if (c.isDark) 0.24f else 0.2f)) {
                com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.tier(top.id, top.tiersReached), Modifier.matchParentSize(), fallback = com.arnavgames.tycoon.ui.common.Art.business(top.id))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to c.background, 0.45f to Color.Transparent, 0.85f to Color.Transparent, 1f to c.background)))
            }
        }
        // Background light.
        Canvas(Modifier.matchParentSize()) {
            frame // redraw every frame
            val gold = if (c.isDark) c.gold else c.goldDeep
            val drewShader = shader != null && Build.VERSION.SDK_INT >= 33 && !shaderBroken[0] && runCatching {
                shader.setFloatUniform("res", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("heat", heat)
                shader.setFloatUniform("frenzy", if (h.frenzyActive) 1f else 0f)
                shader.setColorUniform("gold", gold.toArgb())
                val list = touches.toList()
                listOf("t0", "t1", "t2", "t3").forEachIndexed { i, name ->
                    val t = list.getOrNull(list.size - 1 - i)
                    if (t == null) shader.setFloatUniform(name, 0f, 0f, 0f, 0f)
                    else shader.setFloatUniform(name, t.at.x, t.at.y, time - t.t, 1f)
                }
                drawRect(ShaderBrush(shader))
            }.onFailure { shaderBroken[0] = true; android.util.Log.w("Counter", "shader disabled", it) }.isSuccess
            if (drewShader) {
                // drawn above
            } else {
                val a = (if (c.isDark) 0.16f else 0.22f) + heat * 0.18f + if (h.frenzyActive) 0.2f else 0f
                drawRect(Brush.radialGradient(listOf(gold.copy(alpha = a), Color.Transparent), center = Offset(size.width / 2, size.height * 0.58f), radius = size.maxDimension * 0.7f))
                touches.forEach { t ->
                    val age = time - t.t
                    if (age in 0f..0.85f) {
                        val k = 1f - age / 0.85f
                        drawCircle(c.goldBright.copy(alpha = 0.5f * k), radius = 20.dp.toPx() + 300.dp.toPx() * age, center = t.at, style = Stroke(width = 3.dp.toPx() * k))
                    }
                }
            }
        }

        // The watermark, only until you start.
        if (h.totalTaps < 30) {
            Column(Modifier.align(Alignment.Center).alpha(0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
                AbtIcon(AbtIcons.Hand, null, tint = c.goldText, size = 56.dp)
                Text("Tap anywhere. Use more fingers to serve more customers.", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }

        // Cash, the price per customer and the combo: the only words on the page.
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.scale(pulse.value).onGloballyPositioned {
                    val p = it.positionInRoot() - root
                    cashTarget = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                },
            ) { RollingMoney(e.header.cash, e.header.income, format = fmt, modifier = Modifier.testTag("counter-cash")) }
            Text(
                "${fmt(h.perTap)} a customer" + (if (h.combo > 1) " · combo ×${"%.2f".format(h.comboMultiplier)}" else "") + if (h.frenzyActive) " · frenzy ×3" else "",
                style = Aurum.type.bodySmall, color = if (h.combo > 1 || h.frenzyActive) c.goldText else c.textSecondary,
            )
        }
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = Space.sm, end = Space.md)) {
            FrenzyPill(h.frenzyActive, h.frenzyCharges, countdownText(h.frenzyEndsAtSim - h.simNow)) { vm.dispatch(Command.ActivateFrenzy) }
        }
        guide?.let { g ->
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 128.dp, start = Space.gutter, end = Space.gutter).fillMaxWidth()
                    .glass(AurumShapes.medium, GlassWeight.Regular, blur = false).padding(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 18.dp)
                Spacer(Modifier.width(Space.sm))
                Text(g, style = Aurum.type.bodySmall, color = c.textPrimary)
            }
        }

        // Coins in flight.
        Canvas(Modifier.matchParentSize()) {
            frame
            val rim = if (c.isDark) c.goldDeep else Color(0xFF8A6A1F)
            for (i in 0 until coins.count) {
                val s = coins.size[i]
                val w = s * (0.25f + 0.75f * abs(cos(coins.spin[i])))
                val tl = Offset(coins.x[i] - w / 2, coins.y[i] - s / 2)
                drawOval(rim, tl, Size(w, s))
                drawOval(c.goldFillTop, Offset(tl.x + w * 0.12f, tl.y + s * 0.12f), Size(w * 0.76f, s * 0.76f))
                drawOval(Color.White.copy(alpha = 0.55f), Offset(tl.x + w * 0.28f, tl.y + s * 0.2f), Size(w * 0.22f, s * 0.22f))
            }
        }
        pops.forEach { p -> key(p.id) { EarnedPop(p, reduced) { pops.remove(p) } } }

        // Combo, as a thin line above the tab bar.
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = BottomBarReserve + navInset + Space.sm, start = Space.gutter, end = Space.gutter)
                .fillMaxWidth().heightIn(min = 4.dp).clip(AurumShapes.pill).background(c.separator),
        ) {
            Box(Modifier.fillMaxWidth(heat.coerceIn(0f, 1f)).heightIn(min = 4.dp).clip(AurumShapes.pill)
                .background(Brush.horizontalGradient(listOf(c.goldDeep, c.goldBright))))
        }
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
            when { active -> left; charges > 0 -> "×3 · $charges"; else -> "×3" },
            style = Aurum.type.label, color = if (active) c.textOnGold else if (on) c.textPrimary else c.textTertiary,
        )
    }
}

@Composable
private fun BoxScope.EarnedPop(p: Pop, reduced: Boolean, onDone: () -> Unit) {
    val c = Aurum.colors
    val density = LocalDensity.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(p.id) { anim.animateTo(1f, tween(if (reduced) 1 else 800)); onDone() }
    val rise = with(density) { (72.dp * anim.value).toPx() }
    Text(
        p.text, style = Aurum.type.title, color = c.goldText,
        modifier = Modifier.align(Alignment.TopStart)
            .offset { IntOffset((p.at.x - 36.dp.toPx()).roundToInt(), (p.at.y - 48.dp.toPx() - rise).roundToInt()) }
            .scale(0.8f + 0.4f * (1f - (1f - anim.value) * (1f - anim.value)).coerceAtMost(1f))
            .alpha(1f - anim.value * anim.value),
    )
}
