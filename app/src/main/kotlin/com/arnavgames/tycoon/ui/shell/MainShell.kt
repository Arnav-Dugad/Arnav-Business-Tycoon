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
 * The five main tabs (GDD §16). Content scrolls edge to edge beneath a floating glass tab bar and a
 * frosted status-bar strip. Markets, Estates and Collection unlock in later chapters.
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
    onOpenTiming: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf("empire") }
    val empire by vm.empire.collectAsStateWithLifecycle()
    val claimable = (empire?.claimableCount ?: 0) > 0
    val estatesUi by vm.estates.collectAsStateWithLifecycle()
    val decisions = (estatesUi?.decisions?.size ?: 0) > 0
    val collectionUi by vm.collection.collectAsStateWithLifecycle()
    val auctionLive = collectionUi?.auction != null
    val deskUi by vm.desk.collectAsStateWithLifecycle()
    val dynastyBadge by vm.dynastyBadge.collectAsStateWithLifecycle()
    val cardsWaiting = (deskUi?.cards?.size ?: 0) > 0
    val items = listOf(
        NavItem("empire", "Empire", AbtIcons.Empire, badge = cardsWaiting),
        NavItem("markets", "Markets", AbtIcons.Markets),
        NavItem("estates", "Estates", AbtIcons.Estates, badge = decisions),
        NavItem("collection", "Collection", AbtIcons.Collection, badge = auctionLive),
        NavItem("dynasty", "Dynasty", AbtIcons.Crown, badge = claimable || dynastyBadge),
    )
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
            when (t) {
                "empire" -> EmpireScreen(vm, onOpenBusiness = onOpenBusiness, onOpenDesk = onOpenDesk, onOpenFortune = onOpenFortune, onOpenFinance = onOpenFinance)
                "markets" -> MarketsScreen(vm, onOpenInstrument = onOpenInstrument, onOpenNews = onOpenNews, onOpenTiming = onOpenTiming)
                "estates" -> com.arnavgames.tycoon.ui.estates.EstatesScreen(vm, onOpenCity = onOpenCity, onOpenProperty = onOpenProperty)
                "collection" -> com.arnavgames.tycoon.ui.collection.CollectionScreen(vm, onOpenCategory = onOpenLuxuryCategory, onOpenAuction = onOpenAuction)
                else -> DynastyScreen(vm, onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits, onOpenAchievements = onOpenAchievements, onOpenCrown = onOpenCrown, onOpenStore = onOpenStore, onOpenAccount = onOpenAccount, onOpenSocial = onOpenSocial)
            }
        }
        // Frosted strip behind the status bar so scrolled content blurs under the clock instead of colliding with it.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).glass(RoundedCornerShape(0), GlassWeight.Regular, edge = false))
        AurumBottomBar(items = items, selected = tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
