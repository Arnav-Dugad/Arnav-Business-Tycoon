package com.arnavgames.tycoon.ui.empire

import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.withStyle
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

/**
 * The Empire tab: what to do next (guide, the Chairman, the Next Move, the crown), the world, then every business,
 * what you can open, the merger tree and headquarters. The counter has its own tab ([com.arnavgames.tycoon.ui.counter.CounterScreen]).
 * [focus] scrolls once to "open:<id>" (the card that opens a business), "hq" or "businesses", then calls [onFocusDone].
 */
@Composable
fun EmpireScreen(
    vm: GameViewModel, onOpenBusiness: (String) -> Unit, onOpenDesk: () -> Unit = {}, onOpenFortune: () -> Unit = {}, onOpenFinance: () -> Unit = {},
    onSwitchTab: (String) -> Unit = {}, focus: String? = null, onFocusDone: () -> Unit = {}, onOpenCompany: () -> Unit = {},
) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val qty by vm.buyQty.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val e = ui ?: return
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }

    val mentorLine by vm.mentor.line.collectAsStateWithLifecycle()
    val guide = guideText(e, GuideWhere.Empire)
    val guiding = guide != null
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(e.mission?.chapterNumber, guiding) { if (!guiding) vm.mentorTip(e.mission?.chapterNumber ?: 8) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A new line is inserted above the first card; lazy lists keep the old first item in place, so reveal it.
    androidx.compose.runtime.LaunchedEffect(mentorLine?.id) { if (mentorLine != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0) }

    // The business list in the order the player chose (remembered on this device).
    var bizSort by com.arnavgames.tycoon.ui.common.rememberPref("empire.sort", "default")
    val businesses = when (bizSort) {
        "income" -> e.businesses.sortedByDescending { it.netRate.perSecond() }
        "level" -> e.businesses.sortedByDescending { it.level }
        "tier" -> e.businesses.sortedBy { b -> b.nextTierLevel?.let { it - b.level } ?: Int.MAX_VALUE }
        "name" -> e.businesses.sortedBy { it.name }
        else -> e.businesses
    }
    // Tills with money waiting (businesses without a manager): one tap collects them all.
    val waiting = e.businesses.filter { it.managerName == null && it.stored.isPositive }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reselect.collect { if (it == "empire") listState.animateScrollToItem(0) } }
    val available = e.openable.filter { it.lockedReason == null }
    val locked = e.openable.filter { it.lockedReason != null }.take(2)
    // The list's item keys, in order, so "Show me" can scroll to any card.
    val order = buildList {
        if (guide != null) add("guide")
        add("biz-header")
        businesses.forEach { add(it.id) }
        if (available.isNotEmpty()) { add("open-header"); available.forEach { add("open-" + it.id) } }
        locked.forEach { add("locked-" + it.id) }
    }
    fun scrollTo(target: String) {
        val key = when {
            target.startsWith("open:") -> target.removePrefix("open:").let { id -> if ("open-$id" in order) "open-$id" else if ("locked-$id" in order) "locked-$id" else "biz-header" }
            else -> "biz-header"
        }
        scope.launch { listState.animateScrollToItem(order.indexOf(key).coerceAtLeast(0)) }
    }
    androidx.compose.runtime.LaunchedEffect(focus) { focus?.let { scrollTo(it); onFocusDone() } }

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
            // Empire is your businesses and nothing else (owner request 2026-11-09). The next goal, the Chairman and
            // the crown live on Goals; headquarters and mergers on the Company page; the Desk and friends under More.
            guide?.let { g -> item(key = "guide") { GuideBanner(g) } }
            item(key = "biz-header") {
                Column {
                    SectionHeader("Your businesses") {
                        if (e.businesses.size >= 3) com.arnavgames.tycoon.ui.common.SortToggle(
                            listOf(com.arnavgames.tycoon.ui.common.Choice("default", "Opened"), com.arnavgames.tycoon.ui.common.Choice("income", "Income"), com.arnavgames.tycoon.ui.common.Choice("level", "Level"), com.arnavgames.tycoon.ui.common.Choice("tier", "Next tier"), com.arnavgames.tycoon.ui.common.Choice("name", "A to Z")),
                            bizSort, { bizSort = it }, Modifier.testTag("biz-sort"),
                        )
                    }
                    if (waiting.size >= 2) {
                        val total = waiting.fold(Money.ZERO) { a, b -> a + b.stored }
                        PillAction("Collect all · ${fmt(total)} from ${waiting.size} tills", true, { vm.dispatch(Command.CollectAll) }, style = PillStyle.Positive,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm).testTag("collect-all"))
                    }
                    // Slots and headquarters live on the Company page; this line says how full you are and links there.
                    Row(
                        Modifier.fillMaxWidth().clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small).clickable(onClickLabel = "Open your company") { onOpenCompany() }
                            .padding(vertical = Space.xs).testTag("company-link"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AbtIcon(AbtIcons.Buildings, null, tint = Aurum.colors.goldText, size = 16.dp)
                        Spacer(Modifier.width(Space.xs))
                        Text("${e.hq.slotsUsed} of ${e.hq.slots} business slots · ${e.hq.name}", style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
                        Text("Company", style = Aurum.type.label, color = Aurum.colors.goldText)
                        AbtIcon(AbtIcons.ChevronRight, null, tint = Aurum.colors.goldText, size = 14.dp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Buy", style = Aurum.type.bodySmall, color = Aurum.colors.textSecondary, modifier = Modifier.weight(1f))
                        SegmentedSelector(BuyQty.entries, qty, { it.label }, vm::setBuyQty)
                    }
                }
            }
            items(businesses, key = { it.id }) { b ->
                BusinessCard(
                    b, fmt,
                    onOpen = { onOpenBusiness(b.id) },
                    onBuy = { vm.dispatch(Command.BuyLevels(b.id, if (qty == BuyQty.Max) -1 else qty.count)) },
                    onCollect = { vm.dispatch(Command.Collect(b.id)) },
                )
            }
            if (available.isNotEmpty()) {
                item(key = "open-header") { SectionHeader("Open a new business") }
                items(available, key = { "open-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = { vm.dispatch(Command.OpenBusiness(o.id)) }) }
            }
            items(locked, key = { "locked-" + it.id }) { o -> OpenableCard(o, fmt, onOpen = {}) }
        }
        EmpireHeader(e, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
        // Celebrations appear just below the header so they never cover the numbers.
        MomentOverlay(vm, Modifier.align(Alignment.TopCenter).padding(top = with(density) { headerPx.toDp() }))
    }
}


/** After the last chapter: the next move is the succession. Shows what the heir gains, and that waiting grows it. */
@Composable
internal fun CrownReadyCard(cr: com.arnavgames.tycoon.ui.CrownReadyUi, onOpen: () -> Unit) {
    val c = Aurum.colors
    fun x(m: Double) = "×" + String.format(java.util.Locale.US, if (m < 10) "%.2f" else "%.1f", m)
    AurumCard(Modifier.fillMaxWidth().testTag("crown-ready"), highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR STORY IS COMPLETE", style = Aurum.type.overline, color = c.goldText, modifier = Modifier.weight(1f))
            AbtIcon(AbtIcons.Crown, null, tint = c.goldText, size = 20.dp)
        }
        Spacer(Modifier.height(Space.xs))
        Text("The crown is ready", style = Aurum.type.headline, color = c.textPrimary)
        Text(
            "Pass it to an heir of generation ${cr.generation}. They start again from a kiosk, but every business earns " +
                "${x(cr.multiplierAfter)} instead of ${x(cr.multiplierNow)}, for good. You'd gain ${"%,d".format(cr.legacyGain)} Legacy; " +
                "it grows while you keep building.",
            style = Aurum.type.bodySmall, color = c.textSecondary,
        )
        Spacer(Modifier.height(Space.sm))
        GoldButton("Choose your heir", onClick = onOpen, leadingIcon = AbtIcons.Crown, modifier = Modifier.fillMaxWidth())
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
        var explain by remember { mutableStateOf<com.arnavgames.tycoon.ui.common.Term?>(null) }
        explain?.let { com.arnavgames.tycoon.ui.common.GlossarySheet(it) { explain = null } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CASH", style = Aurum.type.overline, color = c.textTertiary,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "What is cash?") { explain = com.arnavgames.tycoon.ui.common.Term.Cash })
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CurrencyPill(AbtIcons.Bullion, e.header.bullion.toString(), "Bullion", c.bullion) { explain = com.arnavgames.tycoon.ui.common.Term.Bullion }
                CurrencyPill(AbtIcons.Favor, e.header.favor.toString(), "Favor", c.goldText) { explain = com.arnavgames.tycoon.ui.common.Term.Favor }
            }
        }
        RollingMoney(e.header.cash, e.header.income, format = fmt, modifier = Modifier.testTag("header-cash"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AbtIcon(AbtIcons.Chart, null, tint = c.goldText, size = 16.dp)
            Spacer(Modifier.width(Space.xs))
            RollingMoney(e.header.netWorth, e.header.income, style = Aurum.type.number, color = c.textSecondary, format = fmt)
            Spacer(Modifier.width(Space.xs))
            Text("net worth", style = Aurum.type.bodySmall, color = c.textTertiary,
                modifier = Modifier.clickable(onClickLabel = "What is net worth?") { explain = com.arnavgames.tycoon.ui.common.Term.NetWorth })
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
internal fun CurrencyPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        // A full 48 dp touch target around the slim pill.
        Modifier.heightIn(min = Space.touch).clip(AurumShapes.pill).clickable(onClickLabel = "What is $label?", onClick = onClick)
            .padding(vertical = 8.dp)
            .glass(AurumShapes.pill, GlassWeight.Thin, blur = false).padding(horizontal = Space.sm + 2.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AbtIcon(icon, null, tint = tint, size = 14.dp)
        Spacer(Modifier.width(Space.xs))
        Text(value, style = Aurum.type.numberSmall, color = c.textPrimary)
    }
}

// ------------------------------------------------------------------ onboarding

/** Where a guide line is shown: each screen words the step for what you can do from there. */
internal enum class GuideWhere { Empire, Counter }

/** The one instruction a new player sees: a waiting reward comes first, then the current step. */
internal fun guideText(e: EmpireUi, where: GuideWhere): String? = when {
    e.onboardingStep !in 1..4 -> null
    e.mission?.claimable == true ->
        "You've earned a reward: claim it on the Goals tab."
    else -> when (e.onboardingStep) {
        1 -> if (where == GuideWhere.Counter) "Tap anywhere to serve customers. Every customer pays cash." else "Open the Counter tab and serve a few customers."
        2 -> if (where == GuideWhere.Empire) "Buy a level for your kiosk. It earns more every second, even without you." else "Nice. Now open Empire and buy a level for your kiosk."
        3 -> if (where == GuideWhere.Empire) "Your kiosk fills its till while it works. Tap Collect to bank it." else "Your kiosk's till is filling up. Collect it on the Empire tab."
        else -> if (where == GuideWhere.Empire) "Open the kiosk and hire a manager so it runs without you." else "Hire a manager for the kiosk on the Empire tab."
    }
}

/** Advances the onboarding guide when the player has done what it asks. */
@Composable
internal fun OnboardingDriver(e: EmpireUi, vm: GameViewModel) {
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
internal fun GuideBanner(text: String) {
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
internal fun MentorCard(name: String, text: String, onDismiss: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().glassCard(AurumShapes.medium).padding(Space.md).testTag("mentor-card"),
        verticalAlignment = Alignment.Top,
    ) {
        // The Chairman's face (AI caricature of an invented person; docs/ART_PIPELINE.md §3a).
        coil3.compose.AsyncImage(
            model = "file:///android_asset/img/mentor/chairman.webp", contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, c.goldText.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
                .background(c.separator),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = Aurum.type.overline, color = c.goldText)
            // The whole line fades in at once (reduced motion: instantly), so the card is never half-written.
            val line = "“$text”"
            val reduced = Aurum.reducedMotion
            val alpha = remember(line) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
            androidx.compose.runtime.LaunchedEffect(line) { alpha.animateTo(1f, androidx.compose.animation.core.tween(350)) }
            Text(line, style = Aurum.type.body, color = c.textPrimary, modifier = Modifier.graphicsLayer { this.alpha = alpha.value })
        }
        androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            AbtIcon(AbtIcons.Close, "Dismiss", tint = c.textTertiary, size = 16.dp)
        }
    }
}

// ------------------------------------------------------------------ next move

@Composable
internal fun NextMoveCard(m: MissionUi, onClaim: () -> Unit, onGo: (() -> Unit)? = null) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), highlighted = m.claimable) {
        // The chapter's painting heads the card.
        com.arnavgames.tycoon.ui.common.Illustration(
            com.arnavgames.tycoon.ui.common.Art.chapter(m.chapterNumber), Modifier.fillMaxWidth().height(128.dp).clip(AurumShapes.medium), scrim = true,
        ) {
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("CHAPTER ${m.chapterNumber}", style = Aurum.type.overline, color = Color.White.copy(alpha = 0.8f))
                    Text(m.chapterTitle, style = Aurum.type.title, color = Color.White)
                }
                Text("${m.done} of ${m.total}", style = Aurum.type.label, color = Color.White.copy(alpha = 0.85f))
            }
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
                Text("Reward: ${m.reward}", style = Aurum.type.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                if (onGo != null) PillAction("Show me", true, onGo, style = PillStyle.Glass, modifier = Modifier.testTag("show-me"))
            }
        }
    }
}

// ------------------------------------------------------------------ business cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessCard(b: BusinessUi, fmt: (Money) -> String, onOpen: () -> Unit, onBuy: () -> Unit, onCollect: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.arnavgames.tycoon.ui.common.Illustration(
                com.arnavgames.tycoon.ui.common.Art.tier(b.id, b.tiersReached), fallback = com.arnavgames.tycoon.ui.common.Art.business(b.id),
                modifier = Modifier.size(64.dp).clip(AurumShapes.small)
                    .border(if (b.managerName != null) 1.5.dp else 0.dp, if (b.managerName != null) c.gold else Color.Transparent, AurumShapes.small),
            )
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
                    if (b.mothballed) Text("mothballed", style = Aurum.type.label, color = c.textTertiary)
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
        b.bestValue?.let {
            Spacer(Modifier.height(Space.sm))
            AurumChip(it, icon = AbtIcons.StarFilled, tint = c.goldText, modifier = Modifier.testTag("best-value"))
        }
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
                    Text(if (frac >= 1f) "Full · ${fmt(b.stored)}" else "Till ${fmt(b.stored)}", style = Aurum.type.label, color = if (frac >= 1f) c.gain else c.textTertiary, maxLines = 1)
                }
                SmallAction("Collect", enabled = b.stored.isPositive, onClick = onCollect, gold = false)
            } else {
                AurumChip("Auto · ${shortPersonName(b.managerName)}", icon = AbtIcons.Manager, tint = c.goldText)
                Spacer(Modifier.weight(1f))
            }
            Column(horizontalAlignment = Alignment.End) {
                SmallAction("Buy ×${b.buyCount} · ${fmt(b.buyCost)}", enabled = b.canAfford, onClick = onBuy, gold = true)
                if (b.buyGain.isPositive) {
                    // Say plainly when a purchase is poor value (a saturated market, or levels far past the sweet spot).
                    val payback = b.buyCost.toDollarsDouble() / b.buyGain.toDollarsDouble()
                    val slow = payback > 6 * 3600.0
                    Text(
                        "+${MoneyFormat.perSecond(b.buyGain)} income" + if (slow) " · pays back in ${slowPayback(payback)}" else "",
                        style = Aurum.type.label, color = when { !b.canAfford -> c.textTertiary; slow -> c.warning; else -> c.gain },
                        modifier = Modifier.padding(top = 2.dp, end = Space.sm),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit, gold: Boolean) =
    PillAction(text, enabled, onClick, style = if (gold) PillStyle.Gold else PillStyle.Positive)

@Composable
internal fun OpenableCard(o: OpenableUi, fmt: (Money) -> String, onOpen: () -> Unit) {
    val c = Aurum.colors
    val locked = o.lockedReason != null
    AurumCard(Modifier.fillMaxWidth().alpha(if (locked) 0.7f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.arnavgames.tycoon.ui.common.Illustration(
                com.arnavgames.tycoon.ui.common.Art.business(o.id), Modifier.size(64.dp).clip(AurumShapes.small).alpha(if (locked) 0.55f else 1f),
            ) {
                if (locked) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    AbtIcon(AbtIcons.Lock, null, tint = Color.White, size = 20.dp)
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(o.name, style = Aurum.type.title, color = c.textPrimary)
                Text(o.tagline, style = Aurum.type.bodySmall, color = c.textSecondary)
                o.lockedReason?.let { Text(it, style = Aurum.type.label, color = c.textTertiary) }
            }
            if (!locked) {
                Spacer(Modifier.width(Space.sm))
                SmallAction(if (o.cost.isZero) "Open · Free" else "Open · ${fmt(o.cost)}", enabled = o.canAfford, onClick = onOpen, gold = true)
            }
        }
    }
}

@Composable
internal fun HqCard(hq: HqUi, fmt: (Money) -> String, onUpgrade: () -> Unit) {
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.hq(hq.level), Modifier.size(64.dp).clip(AurumShapes.small), fallback = com.arnavgames.tycoon.ui.common.Art.property("office_floor"))
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
internal fun MomentOverlay(vm: GameViewModel, modifier: Modifier) {
    val c = Aurum.colors
    var moment by remember { mutableStateOf<Moment?>(null) }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val m = when (ev) {
                is GameEvent.TierReached -> Moment("New tier: ${ev.tierName}", "Output multiplied at level ${ev.level}.")
                is GameEvent.MissionCompleted -> Moment("Goal complete", "Claim your reward on the Goals tab.")
                is GameEvent.ChapterCompleted -> Moment("Chapter ${ev.chapter} complete", "A new chapter of your story begins.")
                is GameEvent.ExpansionCompleted -> Moment("Expansion complete", "Your business just got bigger.")
                is GameEvent.ManagerHired -> Moment("Manager hired", "This business now runs itself, even offline.")
                is GameEvent.ConglomerateFormed -> Moment("Conglomerate formed", "Its members now share a lasting synergy.")
                is GameEvent.HoldingFormed -> Moment("${ev.name} is born", "Every business you own now earns more.")
                is GameEvent.ShortCovered -> if (ev.forced) Moment("Short position closed", "The price rose too far, so your broker covered it with your collateral.") else null
                is GameEvent.AuctionWon -> Moment("Sold to you!", "The hammer fell in your favour. Find it in your showroom.")
                is GameEvent.AuctionLost -> Moment("Outbid", if (ev.winner.isNotEmpty()) "${ev.winner} took the lot this time." else "The lot went to another bidder.")
                is GameEvent.Foreclosure -> Moment("Property repossessed", "The mortgage outgrew the property's value, so the bank sold it.")
                is GameEvent.TowerCompleted -> Moment("Skyscraper complete", "Floors lease one an hour, and the neighbourhood's prices just rose.")
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

/** The guide line for the Counter tab (null once the tutorial is done). */
fun counterGuide(e: EmpireUi): String? = guideText(e, GuideWhere.Counter)

/** "9 h", "13 days", "over a year": how long a slow purchase takes to earn back its price. */
internal fun slowPayback(sec: Double): String = when {
    !sec.isFinite() -> "never"
    sec < 172_800 -> "${(sec / 3600).toInt()} h"
    sec < 365.0 * 86_400 -> "${(sec / 86_400).toInt()} days"
    else -> "over a year"
}
