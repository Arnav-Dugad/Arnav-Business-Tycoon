package com.arnavgames.tycoon.ui.empire

import com.arnavgames.tycoon.ui.common.shortPersonName
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.GoldProgress
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.RollingMoney
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.BuyQty
import com.arnavgames.tycoon.ui.BusinessUi
import com.arnavgames.tycoon.ui.EmpireUi
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.HqUi
import com.arnavgames.tycoon.ui.HustleUi
import com.arnavgames.tycoon.ui.MissionUi
import com.arnavgames.tycoon.ui.OpenableUi
import com.arnavgames.tycoon.ui.common.countdownText
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassCard
import com.arnavgames.tycoon.designsystem.theme.glassSource

@Composable
fun EmpireScreen(vm: GameViewModel, onOpenBusiness: (String) -> Unit, onOpenDesk: () -> Unit = {}, onOpenFortune: () -> Unit = {}, onOpenFinance: () -> Unit = {}) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val qty by vm.buyQty.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val e = ui ?: return
    val fmt: (Money) -> String = { MoneyFormat.short(it, notation) }

    OnboardingDriver(e, vm)
    val mentorLine by vm.mentor.line.collectAsStateWithLifecycle()
    val guiding = guideText(e) != null
    androidx.compose.runtime.LaunchedEffect(e.mission?.chapterNumber, guiding) { if (!guiding) vm.mentorTip(e.mission?.chapterNumber ?: 8) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A new line is inserted above the first card; lazy lists keep the old first item in place, so reveal it.
    androidx.compose.runtime.LaunchedEffect(mentorLine?.id) { if (mentorLine != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0) }

    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        // Content scrolls beneath the frosted header and tab bar (it is the glass "content" layer).
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Space.gutter, end = Space.gutter,
                top = with(density) { headerPx.toDp() } + Space.md,
                bottom = BottomBarReserve + navInset + Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content).testTag("empire-list"),
        ) {
            guideText(e)?.let { g -> item(key = "guide") { GuideBanner(g) } }
            if (!guiding) mentorLine?.let { l -> item(key = "mentor") { MentorCard(vm.mentor.name, l.text, onDismiss = { vm.mentor.dismiss() }) } }
            e.mission?.let { m -> item(key = "mission") { NextMoveCard(m, onClaim = { vm.dispatch(Command.ClaimMission(m.id)) }) } }
            item(key = "world") { com.arnavgames.tycoon.ui.world.WorldStrip(vm, onOpenDesk, onOpenFortune, onOpenFinance) }
            item(key = "hustle") {
                HustleCard(e.hustle, fmt, onTap = { vm.dispatch(Command.HustleTap(1)) }, onFrenzy = { vm.dispatch(Command.ActivateFrenzy) })
            }
            item(key = "biz-header") {
                Column {
                    SectionHeader("Your businesses") {
                        Text("${e.hq.slotsUsed}/${e.hq.slots} slots", style = Aurum.type.label, color = Aurum.colors.textTertiary)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Buy amount", style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
                        SegmentedSelector(BuyQty.entries, qty, { it.label }, vm::setBuyQty)
                    }
                }
            }
            items(e.businesses, key = { it.id }) { b ->
                BusinessCard(
                    b, fmt,
                    onOpen = { onOpenBusiness(b.id) },
                    onBuy = { vm.dispatch(Command.BuyLevels(b.id, if (qty == BuyQty.Max) -1 else qty.count)) },
                    onCollect = { vm.dispatch(Command.Collect(b.id)) },
                )
            }
            val available = e.openable.filter { it.lockedReason == null }
            val locked = e.openable.filter { it.lockedReason != null }.take(2)
            if (available.isNotEmpty()) {
                item(key = "open-header") { SectionHeader("Open a new business") }
                items(available, key = { "open-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = { vm.dispatch(Command.OpenBusiness(o.id)) }) }
            }
            if (locked.isNotEmpty()) {
                items(locked, key = { "locked-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = {}) }
            }
            if (e.mergers.isNotEmpty()) {
                item(key = "merger-header") { SectionHeader("Merger tree") }
                items(e.mergers, key = { "merger-" + it.id }) { m -> MergerCard(m, fmt, onForm = { vm.dispatch(Command.FormConglomerate(m.id)) }) }
                e.holding?.let { h -> item(key = "holding") { HoldingCard(h, fmt, onForm = { name -> vm.dispatch(Command.FormHolding(name)) }) } }
            }
            item(key = "hq-header") { SectionHeader("Headquarters") }
            item(key = "hq") { HqCard(e.hq, fmt, onUpgrade = { vm.dispatch(Command.UpgradeHq) }) }
        }
        EmpireHeader(e, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
        MomentOverlay(vm, Modifier.align(Alignment.TopCenter))
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun EmpireHeader(e: EmpireUi, fmt: (Money) -> String, modifier: Modifier = Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.md, bottom = Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NET WORTH", style = Aurum.type.overline, color = c.textTertiary, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CurrencyPill(AbtIcons.Bullion, e.header.bullion.toString(), "Bullion", c.bullion)
                CurrencyPill(AbtIcons.Favor, e.header.favor.toString(), "Favor", c.goldText)
            }
        }
        RollingMoney(e.header.netWorth, e.header.income, format = fmt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Cash, null, tint = c.goldText, size = 16.dp)
            Spacer(Modifier.width(Space.xs))
            RollingMoney(e.header.cash, e.header.income, style = Aurum.type.number, color = c.textPrimary, format = fmt)
            Spacer(Modifier.width(Space.xs))
            Text("cash", style = Aurum.type.bodySmall, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            if (!e.header.income.isZero) {
                AbtIcon(AbtIcons.ArrowUp, null, tint = c.gain, size = 14.dp)
                Text(MoneyFormat.perSecond(e.header.income.perSecond()), style = Aurum.type.numberSmall, color = c.gain)
            }
        }
    }
    HairlineDivider()
    }
}

@Composable
private fun CurrencyPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: androidx.compose.ui.graphics.Color) {
    val c = Aurum.colors
    Row(
        Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false).padding(horizontal = Space.sm + 2.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(icon, null, tint = tint, size = 14.dp)
        Spacer(Modifier.width(Space.xs))
        Text(value, style = Aurum.type.numberSmall, color = c.textPrimary)
    }
}

// ------------------------------------------------------------------ onboarding

private fun guideText(e: EmpireUi): String? = when (e.onboardingStep) {
    1 -> "Tap the counter to serve your first customers."
    2 -> "Nice! Buy an upgrade for your kiosk to earn more each second."
    3 -> "Unmanaged businesses fill their till. Tap Collect to bank it."
    4 -> "Keep growing. A manager will run the kiosk for you — check the Next Move card."
    else -> null
}

/** Advances the onboarding guide when the player has done what it asks. */
@Composable
private fun OnboardingDriver(e: EmpireUi, vm: GameViewModel) {
    val step = e.onboardingStep
    val retail = e.businesses.firstOrNull { it.id == "retail" }
    val next = when {
        step == 1 && e.hustle.totalTaps >= 8 -> 2
        step == 2 && (retail?.level ?: 0) >= 2 -> 3
        step == 3 && (retail?.level ?: 0) >= 5 -> 4
        step == 4 && e.businesses.any { it.managerName != null } -> 5
        else -> null
    }
    LaunchedEffect(next) { if (next != null) vm.dispatch(Command.AdvanceOnboarding(next)) }
}

@Composable
private fun GuideBanner(text: String) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().glassCard(AurumShapes.medium, highlighted = true).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(AbtIcons.Sparkle, null, tint = c.goldText, size = 20.dp)
        Spacer(Modifier.width(Space.md))
        Text(text, style = Aurum.type.body, color = c.textPrimary)
    }
}

/** The Chairman's line: one at a time, dismissible. */
@Composable
private fun MentorCard(name: String, text: String, onDismiss: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().glassCard(AurumShapes.medium).padding(Space.md).testTag("mentor-card"),
        verticalAlignment = Alignment.Top,
    ) {
        AbtIcon(AbtIcons.Bust, null, tint = c.goldText, size = 22.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = Aurum.type.overline, color = c.goldText)
            Text("“$text”", style = Aurum.type.body, color = c.textPrimary)
        }
        androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            AbtIcon(AbtIcons.Close, "Dismiss", tint = c.textTertiary, size = 16.dp)
        }
    }
}

// ------------------------------------------------------------------ next move

@Composable
private fun NextMoveCard(m: MissionUi, onClaim: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), highlighted = m.claimable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CHAPTER ${m.chapterNumber} · ${m.chapterTitle.uppercase()}", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
            Text("${m.done}/${m.total}", style = Aurum.type.numberSmall, color = c.textTertiary)
        }
        Spacer(Modifier.height(Space.sm))
        Text(m.title, style = Aurum.type.headline, color = c.textPrimary)
        Spacer(Modifier.height(Space.xxs))
        Text(m.description, style = Aurum.type.body, color = c.textSecondary)
        Spacer(Modifier.height(Space.md))
        if (m.claimable) {
            GoldButton("Claim ${m.reward}", onClick = onClaim, modifier = Modifier.fillMaxWidth(), leadingIcon = AbtIcons.Gift)
        } else {
            GoldProgress(m.progress, label = "Mission progress ${(m.progress * 100).toInt()} percent")
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Gift, null, tint = c.textTertiary, size = 14.dp)
                Spacer(Modifier.width(Space.xs))
                Text("Reward: ${m.reward}", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
    }
}

// ------------------------------------------------------------------ hustle

private data class Pop(val id: Long, val text: String)

@Composable
private fun HustleCard(h: HustleUi, fmt: (Money) -> String, onTap: () -> Unit, onFrenzy: () -> Unit) {
    val c = Aurum.colors
    val pops = remember { mutableStateListOf<Pop>() }
    var popId by remember { mutableStateOf(0L) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = Aurum.reducedMotion
    AurumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                Box(
                    Modifier
                        .size(96.dp)
                        .scale(if (pressed && !reduced) 0.93f else 1f)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(c.goldFillTop, c.goldFillBottom, c.goldDeep)))
                        .border(1.5.dp, Brush.verticalGradient(listOf(c.goldBright, c.goldDeep)), CircleShape)
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = size.height * 0.5f))
                        }
                        .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Serve a customer") {
                            onTap()
                            popId++
                            pops.add(Pop(popId, "+" + fmt(h.perTap)))
                            if (pops.size > 6) pops.removeAt(0)
                        }
                        .semantics { contentDescription = "Serve a customer, earns ${fmt(h.perTap)}" },
                    contentAlignment = Alignment.Center,
                ) {
                    AbtIcon(AbtIcons.Hand, null, tint = c.textOnGold, size = 40.dp)
                }
                pops.forEach { p -> androidx.compose.runtime.key(p.id) { FloatingPop(p, onDone = { pops.remove(p) }) } }
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text("Work the counter", style = Aurum.type.title, color = c.textPrimary)
                Text("${fmt(h.perTap)} per customer", style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.sm))
                val comboFrac = h.combo.toFloat() / h.maxCombo
                GoldProgress(comboFrac, height = 5.dp, label = "Combo ${h.combo}")
                Spacer(Modifier.height(Space.xxs))
                Text(if (h.combo > 1) "Combo ×${"%.2f".format(h.comboMultiplier)}" else "Tap quickly to build a combo", style = Aurum.type.label, color = c.textTertiary)
                Spacer(Modifier.height(Space.sm))
                if (h.frenzyActive) {
                    AurumChip("Frenzy ×3 · ${countdownText(h.frenzyEndsAtSim - h.simNow)}", icon = AbtIcons.Bolt, tint = c.textOnGold, background = c.gold)
                } else {
                    Row(
                        Modifier.clip(AurumShapes.pill).border(1.dp, if (h.frenzyCharges > 0) c.gold else c.outline, AurumShapes.pill)
                            .clickable(enabled = h.frenzyCharges > 0, role = Role.Button, onClick = onFrenzy)
                            .heightIn(min = 36.dp).padding(horizontal = Space.md, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AbtIcon(AbtIcons.Bolt, null, tint = if (h.frenzyCharges > 0) c.goldText else c.textTertiary, size = 16.dp)
                        Spacer(Modifier.width(Space.xs))
                        Text("Frenzy ×3 · ${h.frenzyCharges} left", style = Aurum.type.label, color = if (h.frenzyCharges > 0) c.textPrimary else c.textTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPop(p: Pop, onDone: () -> Unit) {
    val c = Aurum.colors
    val anim = remember { Animatable(0f) }
    val reduced = Aurum.reducedMotion
    LaunchedEffect(p.id) {
        anim.animateTo(1f, tween(if (reduced) 1 else 900))
        onDone()
    }
    Text(
        p.text, style = Aurum.type.titleSmall, color = c.goldText,
        modifier = Modifier.offset(y = (-58 - 50 * anim.value).dp, x = ((p.id % 5) - 2).toInt().times(10).dp).alpha(1f - anim.value),
    )
}

// ------------------------------------------------------------------ business cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessCard(b: BusinessUi, fmt: (Money) -> String, onOpen: () -> Unit, onBuy: () -> Unit, onCollect: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.named(b.icon), highlighted = b.managerName != null)
            Spacer(Modifier.width(Space.md))
            // At large font sizes the income moves under the name so long names never break mid-word.
            val stacked = LocalDensity.current.fontScale > 1.35f
            val rateText = MoneyFormat.perSecond(b.netRate.perSecond())
            Column(Modifier.weight(1f)) {
                Text(b.name, style = Aurum.type.title, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${b.tierName} · Lv ${b.level}", style = Aurum.type.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (stacked) Text(if (b.mothballed) "Mothballed" else rateText, style = Aurum.type.number, color = if (b.mothballed) c.textTertiary else c.textPrimary)
            }
            if (!stacked) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(rateText, style = Aurum.type.number, color = if (b.mothballed) c.textTertiary else c.textPrimary)
                    Text(if (b.mothballed) "mothballed" else "per second", style = Aurum.type.label, color = c.textTertiary)
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        GoldProgress(b.tierProgress, height = 4.dp, label = "Progress to next tier")
        Spacer(Modifier.height(Space.xs))
        Text(
            b.nextTierLevel?.let { "Next tier at level $it" } ?: "Top tier reached",
            style = Aurum.type.label, color = c.textTertiary,
        )
        b.hint?.let {
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Sparkle, null, tint = c.warning, size = 14.dp)
                Spacer(Modifier.width(Space.xs))
                Text(it, style = Aurum.type.bodySmall, color = c.warning)
            }
        }
        b.timer?.let { t ->
            Spacer(Modifier.height(Space.sm))
            AurumChip("${t.label} · building", icon = AbtIcons.Timer, tint = c.info)
        }
        Spacer(Modifier.height(Space.md))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (b.managerName == null) {
                Column(Modifier.weight(1f).widthIn(min = 96.dp)) {
                    val frac = if (b.storageCap.isPositive) b.stored.ratioTo(b.storageCap).toFloat() else 0f
                    GoldProgress(frac, height = 4.dp, brush = Brush.horizontalGradient(listOf(c.gain.copy(alpha = 0.7f), c.gain)), label = "Till ${(frac * 100).toInt()} percent full")
                    Spacer(Modifier.height(Space.xs))
                    Text(if (frac >= 1f) "Till full" else "Till ${fmt(b.stored)}", style = Aurum.type.label, color = if (frac >= 1f) c.gain else c.textTertiary, maxLines = 1)
                }
                SmallAction("Collect", enabled = b.stored.isPositive, onClick = onCollect, gold = false)
            } else {
                AurumChip("Auto · ${shortPersonName(b.managerName)}", icon = AbtIcons.Manager, tint = c.goldText)
                Spacer(Modifier.weight(1f))
            }
            SmallAction("Buy ×${b.buyCount} · ${fmt(b.buyCost)}", enabled = b.canAfford, onClick = onBuy, gold = true)
        }
    }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit, gold: Boolean) =
    PillAction(text, enabled, onClick, style = if (gold) PillStyle.Gold else PillStyle.Positive)

@Composable
private fun OpenableCard(o: OpenableUi, fmt: (Money) -> String, onOpen: () -> Unit) {
    val c = Aurum.colors
    val locked = o.lockedReason != null
    AurumCard(Modifier.fillMaxWidth().alpha(if (locked) 0.7f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (locked) AbtIcons.Lock else AbtIcons.named(o.icon), tint = if (locked) c.textTertiary else c.goldText)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = Aurum.type.title, color = c.textPrimary)
                Text(o.lockedReason ?: o.tagline, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
            if (!locked) {
                Spacer(Modifier.width(Space.sm))
                SmallAction(if (o.cost.isZero) "Open · Free" else "Open · ${fmt(o.cost)}", enabled = o.canAfford, onClick = onOpen, gold = true)
            }
        }
    }
}

@Composable
private fun HqCard(hq: HqUi, fmt: (Money) -> String, onUpgrade: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AbtIcons.Empire)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(hq.name, style = Aurum.type.title, color = c.textPrimary)
                Text("Level ${hq.level} · ${hq.slots} slots · ${"%.1f".format(hq.offlineCapHours).removeSuffix(".0")}h offline earnings", style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        if (hq.nextName != null && hq.nextCost != null) {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next: ${hq.nextName}", style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                SmallAction("Upgrade · ${fmt(hq.nextCost)}", enabled = hq.canAfford, onClick = onUpgrade, gold = true)
            }
        }
    }
}

// ------------------------------------------------------------------ moments

private data class Moment(val title: String, val body: String)

/** Short, celebratory banner for milestones (tier reached, mission complete, chapter complete). */
@Composable
private fun MomentOverlay(vm: GameViewModel, modifier: Modifier) {
    val c = Aurum.colors
    var moment by remember { mutableStateOf<Moment?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val m = when (ev) {
                is GameEvent.TierReached -> Moment("New tier: ${ev.tierName}", "Output multiplied at level ${ev.level}.")
                is GameEvent.MissionCompleted -> Moment("Goal complete", "Claim your reward on the Next Move card.")
                is GameEvent.ChapterCompleted -> Moment("Chapter ${ev.chapter} complete", "A new chapter of your story begins.")
                is GameEvent.ExpansionCompleted -> Moment("Expansion complete", "Your business just got bigger.")
                is GameEvent.ManagerHired -> Moment("Manager hired", "This business now runs itself, even offline.")
                is GameEvent.ConglomerateFormed -> Moment("Conglomerate formed", "Its members now share a lasting synergy.")
                is GameEvent.HoldingFormed -> Moment("${ev.name} is born", "Every business you own now earns more.")
                is GameEvent.ShortCovered -> if (ev.forced) Moment("Short position closed", "The price rose too far, so your broker covered it with your collateral.") else null
                is GameEvent.AuctionWon -> Moment("Sold to you!", "The hammer fell in your favour. Find it in your showroom.")
                is GameEvent.AuctionLost -> Moment("Outbid", if (ev.winner.isNotEmpty()) "${ev.winner} took the lot this time." else "The lot went to another bidder.")
                is GameEvent.Foreclosure -> Moment("Property repossessed", "The mortgage outgrew the property's value, so the bank sold it.")
                is GameEvent.TowerCompleted -> Moment("Skyscraper complete", "Floors lease one an hour, and the district's prices just rose.")
                is GameEvent.Disaster -> if (!ev.insured) Moment("Disaster damage", "One of your properties was damaged. Repair it on the Estates tab.") else null
                is GameEvent.EarningsSeason -> Moment("Earnings season", "${ev.beats} companies beat estimates, ${ev.misses} missed. Check the Markets tab.")
                else -> null
            }
            if (m != null) {
                moment = m
                delay(2_400)
                if (moment == m) moment = null
            }
        }
    }
    AnimatedVisibility(
        visible = moment != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding().padding(top = Space.sm, start = Space.gutter, end = Space.gutter),
    ) {
        val m = moment ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .glass(AurumShapes.large, GlassWeight.Thick)
                .padding(Space.md)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(AbtIcons.Sparkle, size = 40.dp, highlighted = true)
            Spacer(Modifier.width(Space.md))
            Column {
                Text(m.title, style = Aurum.type.title, color = c.textPrimary)
                Text(m.body, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
    }
}
