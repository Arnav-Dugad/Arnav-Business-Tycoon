package com.arnavgames.tycoon.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Brush
import com.arnavgames.tycoon.designsystem.theme.Aurum
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.designsystem.components.AurumBottomBar
import com.arnavgames.tycoon.designsystem.components.NavItem
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.dynasty.DynastyScreen
import com.arnavgames.tycoon.ui.empire.EmpireScreen
import com.arnavgames.tycoon.ui.locked.ComingSoonScreen

/** The five main tabs (GDD §16). Markets, Estates and Collection unlock in later chapters. */
@Composable
fun MainShell(vm: GameViewModel, onOpenBusiness: (String) -> Unit, onOpenSettings: () -> Unit, onOpenCredits: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("empire") }
    val empire by vm.empire.collectAsStateWithLifecycle()
    val claimable = (empire?.claimableCount ?: 0) > 0
    val items = listOf(
        NavItem("empire", "Empire", AbtIcons.Empire),
        NavItem("markets", "Markets", AbtIcons.Markets),
        NavItem("estates", "Estates", AbtIcons.Estates),
        NavItem("collection", "Collection", AbtIcons.Collection),
        NavItem("dynasty", "Dynasty", AbtIcons.Crown, badge = claimable),
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                when (t) {
                    "empire" -> EmpireScreen(vm, onOpenBusiness = onOpenBusiness)
                    "markets" -> ComingSoonScreen(
                        icon = AbtIcons.Markets, title = "Markets", chapter = 2,
                        lines = listOf("120 fictional companies across 12 sectors", "News that moves prices, dividends and earnings seasons", "Funds, bonds, commodities and, later, crypto"),
                    )
                    "estates" -> ComingSoonScreen(
                        icon = AbtIcons.Estates, title = "Estates", chapter = 3,
                        lines = listOf("Six cities and forty districts on a living market cycle", "Rent, renovate and flip properties", "Build your own skyscraper"),
                    )
                    "collection" -> ComingSoonScreen(
                        icon = AbtIcons.Collection, title = "Collection", chapter = 3,
                        lines = listOf("Cars, jets, yachts, islands and masterpieces", "Live auctions against rival billionaires", "Showrooms and set bonuses"),
                    )
                    else -> DynastyScreen(vm, onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits)
                }
            }
            // Scrim behind the status bar so scrolled content never collides with the clock and icons.
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Aurum.colors.background))
                Box(
                    Modifier.fillMaxWidth().height(14.dp)
                        .background(Brush.verticalGradient(listOf(Aurum.colors.background, Aurum.colors.background.copy(alpha = 0f)))),
                )
            }
        }
        AurumBottomBar(items = items, selected = tab, onSelect = { tab = it })
    }
}
