package com.arnavgames.tycoon.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.designsystem.components.AurumBottomBar
import com.arnavgames.tycoon.designsystem.components.NavItem
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.dynasty.DynastyScreen
import com.arnavgames.tycoon.ui.empire.EmpireScreen
import com.arnavgames.tycoon.ui.locked.ComingSoonScreen
import com.arnavgames.tycoon.ui.markets.MarketsScreen

/**
 * The five main tabs: Counter (the tapping game, full screen), Empire (what to do next and every business), Markets,
 * Estates and More (Collection, Dynasty, Store, account, settings). Content scrolls edge to edge beneath a floating glass tab bar and a frosted status-bar strip.
 */
@Composable
fun MainShell(
    vm: GameViewModel,
    onOpenBusiness: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenInstrument: (String) -> Unit = {},
    onOpenNews: () -> Unit = {},
    onOpenCity: (String) -> Unit = {},
    onOpenProperty: (Long) -> Unit = {},
    onOpenLuxuryCategory: (String) -> Unit = {},
    onOpenAuction: () -> Unit = {},
    onOpenDesk: () -> Unit = {},
    onOpenFortune: () -> Unit = {},
    onOpenFinance: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenCrown: () -> Unit = {},
    onOpenStore: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenSocial: () -> Unit = {},
    onOpenSlots: () -> Unit = {},
    onOpenCompany: () -> Unit = {},
    onOpenCheats: () -> Unit = {},
) {
    // Chosen once the game has loaded: a brand-new player starts at the Counter, everyone else on Empire.
    var chosenTab by rememberSaveable { mutableStateOf("") }
    // Inside More: the hub, or the Collection or Dynasty page (with a back button).
    var morePage by rememberSaveable { mutableStateOf("hub") }
    // A one-shot scroll target for the Empire tab ("open:<id>", "hq" or "businesses").
    var businessFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val empire by vm.empire.collectAsStateWithLifecycle()
    val claimable = (empire?.claimableCount ?: 0) > 0
    val estatesUi by vm.estates.collectAsStateWithLifecycle()
    val decisions = (estatesUi?.decisions?.size ?: 0) > 0
    val collectionUi by vm.collection.collectAsStateWithLifecycle()
    val auctionLive = collectionUi?.auction != null
    val deskUi by vm.desk.collectAsStateWithLifecycle()
    val dynastyBadge by vm.dynastyBadge.collectAsStateWithLifecycle()
    val cardsWaiting = (deskUi?.cards?.size ?: 0) > 0
    val e = empire
    androidx.compose.runtime.LaunchedEffect(e != null) { if (chosenTab.isEmpty() && e != null) chosenTab = if (e.onboardingStep <= 1) "counter" else "empire" }
    val tab = chosenTab.ifEmpty { "empire" }
    // Empire wants you when a till is full (no manager), a reward waits, a decision waits, or the guide points there.
    val empireBadge = e != null && (e.onboardingStep in 2..4 ||
        e.businesses.any { it.managerName == null && it.storageCap.isPositive && it.stored >= it.storageCap })
    val items = listOf(
        NavItem("counter", "Counter", AbtIcons.Hand),
        NavItem("empire", "Empire", AbtIcons.Empire, badge = cardsWaiting || empireBadge),
        NavItem("markets", "Markets", AbtIcons.Markets),
        NavItem("estates", "Estates", AbtIcons.Estates, badge = decisions),
        // Goals: every reward in one tab (daily reward, chapter, today, this week, achievements).
        NavItem("goals", "Goals", AbtIcons.Trophy, badge = dynastyBadge || claimable),
        NavItem("more", "More", AbtIcons.Menu, badge = auctionLive),
    )
    // The tutorial advances from whichever tab the player is on.
    e?.let { com.arnavgames.tycoon.ui.empire.OnboardingDriver(it, vm) }
    val switchTo: (String) -> Unit = { target ->
        when {
            target == "collection" || target == "dynasty" -> { morePage = target; chosenTab = "more" }
            target == "goals" || target == "missions" -> chosenTab = "goals"
            target.startsWith("businesses:") -> { businessFocus = target.removePrefix("businesses:"); chosenTab = "empire" }
            target == "businesses" -> { businessFocus = "businesses"; chosenTab = "empire" }
            else -> chosenTab = target
        }
    }
    val tabRequest by vm.tabRequest.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(tabRequest) { tabRequest?.let { switchTo(it); vm.consumeTabRequest() } }
    androidx.compose.runtime.LaunchedEffect(tab, morePage) { vm.music.setScene(if (tab == "more" && morePage != "hub") morePage else tab) }
    androidx.activity.compose.BackHandler(enabled = tab == "more" && morePage != "hub") { morePage = "hub" }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab to morePage, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { (t, page) ->
            when (t) {
                "counter" -> com.arnavgames.tycoon.ui.counter.CounterScreen(vm, guide = e?.let { com.arnavgames.tycoon.ui.empire.counterGuide(it) })
                "empire" -> EmpireScreen(
                    vm, onOpenBusiness = onOpenBusiness, onOpenDesk = onOpenDesk, onOpenFortune = onOpenFortune, onOpenFinance = onOpenFinance,
                    onSwitchTab = switchTo, focus = businessFocus, onFocusDone = { businessFocus = null }, onOpenCompany = onOpenCompany,
                )
                "markets" -> MarketsScreen(vm, onOpenInstrument = onOpenInstrument, onOpenNews = onOpenNews)
                "estates" -> com.arnavgames.tycoon.ui.estates.EstatesScreen(vm, onOpenCity = onOpenCity, onOpenProperty = onOpenProperty)
                "goals" -> com.arnavgames.tycoon.ui.dynasty.GoalsScreen(
                    vm, onOpenAchievements = onOpenAchievements, onOpenDynasty = { morePage = "dynasty"; chosenTab = "more" },
                    onOpenBusiness = onOpenBusiness, onSwitchTab = switchTo, onOpenCompany = onOpenCompany,
                )
                else -> when (page) {
                    "collection" -> com.arnavgames.tycoon.ui.collection.CollectionScreen(vm, onOpenCategory = onOpenLuxuryCategory, onOpenAuction = onOpenAuction, onBack = { morePage = "hub" })
                    "dynasty" -> DynastyScreen(
                        vm, onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits, onOpenAchievements = onOpenAchievements, onOpenCrown = onOpenCrown,
                        onOpenStore = onOpenStore, onOpenAccount = onOpenAccount, onOpenSocial = onOpenSocial, onBack = { morePage = "hub" },
                    )
                    else -> com.arnavgames.tycoon.ui.more.MoreScreen(
                        vm, onOpenCollection = { morePage = "collection" }, onOpenDynasty = { morePage = "dynasty" },
                        onOpenStore = onOpenStore, onOpenAchievements = onOpenAchievements, onOpenSocial = onOpenSocial, onOpenAccount = onOpenAccount,
                        onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits,
                        onOpenDesk = onOpenDesk, onOpenFortune = onOpenFortune, onOpenFinance = onOpenFinance,
                        onOpenSlots = onOpenSlots, onOpenCheats = onOpenCheats, onOpenCompany = onOpenCompany,
                    )
                }
            }
        }
        // Frosted strip behind the status bar so scrolled content blurs under the clock instead of colliding with it.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).glass(RoundedCornerShape(0), GlassWeight.Regular, edge = false))
        AurumBottomBar(
            items = items.map { if (it.key == tab) it.copy(badge = false) else it }, selected = tab,
            // Tapping the tab you are on takes you back to its top (and More back to its hub), like every good app.
            onSelect = { t -> if (t == tab) vm.reselectTab(t); if (t == "more" && tab == "more") morePage = "hub"; chosenTab = t },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
