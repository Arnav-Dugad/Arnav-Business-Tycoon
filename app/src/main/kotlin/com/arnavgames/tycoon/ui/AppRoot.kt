package com.arnavgames.tycoon.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.data.settings.ThemePref
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.AmbientBackdrop
import com.arnavgames.tycoon.designsystem.theme.AurumTheme
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.LocalGlassState
import com.arnavgames.tycoon.designsystem.theme.glass
import dev.chrisbanes.haze.rememberHazeState
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.ThemeMode
import com.arnavgames.tycoon.game.GameSession
import com.arnavgames.tycoon.ui.business.BusinessDetailScreen
import com.arnavgames.tycoon.ui.common.LoadingScreen
import com.arnavgames.tycoon.ui.common.OfflineEarningsDialog
import com.arnavgames.tycoon.ui.common.RecoveryScreen
import com.arnavgames.tycoon.ui.onboarding.WelcomeScreen
import com.arnavgames.tycoon.ui.settings.CreditsScreen
import com.arnavgames.tycoon.ui.settings.SettingsScreen
import com.arnavgames.tycoon.ui.shell.MainShell
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Serializable object MainRoute
@Serializable data class BusinessRoute(val id: String)
@Serializable object CounterRoute
@Serializable object SettingsRoute
@Serializable object CreditsRoute
@Serializable data class InstrumentRoute(val id: String)
@Serializable object NewsRoute
@Serializable data class CityRoute(val id: String)
@Serializable data class DistrictRoute(val id: String)
@Serializable data class PropertyRoute(val id: Long)
@Serializable data class LuxuryCategoryRoute(val id: String)
@Serializable data class LuxuryItemRoute(val id: String)
@Serializable object AuctionRoute
@Serializable object DeskRoute
@Serializable object FortuneRoute
@Serializable data class RivalRoute(val id: String)
@Serializable object FinanceRoute
@Serializable object TaxRoute
@Serializable object AchievementsRoute
@Serializable object CrownRoute
@Serializable object StoreRoute
@Serializable object AccountRoute
@Serializable object SocialRoute
@Serializable object TimingRoute

@Composable
fun AppRoot(vm: GameViewModel, forcedTheme: ThemeMode?, onDarkChanged: (Boolean) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val mode = forcedTheme ?: when (settings.theme) {
        ThemePref.System -> ThemeMode.System
        ThemePref.Dark -> ThemeMode.Dark
        ThemePref.Light -> ThemeMode.Light
    }
    AurumTheme(mode = mode, reducedMotion = settings.reducedMotion) {
        val dark = Aurum.colors.isDark
        LaunchedEffect(dark) { onDarkChanged(dark) }
        val status by vm.status.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val haptics = LocalHapticFeedback.current

        LaunchedEffect(Unit) { vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }
        LaunchedEffect(settings.musicVolume) { vm.music.setVolume(settings.musicVolume) }
        LaunchedEffect(settings.sfxVolume) {
            if (settings.sfxVolume <= 0f) return@LaunchedEffect
            vm.events.collect { e -> vm.sounds.onEvent(e, settings.sfxVolume) }
        }
        LaunchedEffect(settings.haptics) {
            if (!settings.haptics) return@LaunchedEffect
            vm.events.collect { e ->
                // Haptic vocabulary (docs/PLAN_2.0.md F2): ticks for routine, a pulse for tiers, a heavy press for
                // milestones, a buzz for bad news.
                val type = when (e) {
                    is GameEvent.HustleEarned -> HapticFeedbackType.SegmentTick
                    is GameEvent.LevelsBought, is GameEvent.Collected, is GameEvent.AuctionBid -> HapticFeedbackType.ContextClick
                    is GameEvent.TierReached -> HapticFeedbackType.GestureThresholdActivate
                    is GameEvent.ChapterCompleted, is GameEvent.ConglomerateFormed, is GameEvent.HoldingFormed, is GameEvent.CrownPassed,
                    is GameEvent.IpoLaunched, is GameEvent.TowerCompleted, is GameEvent.AuctionWon -> HapticFeedbackType.LongPress
                    is GameEvent.ManagerHired, is GameEvent.BusinessOpened, is GameEvent.MissionClaimed, is GameEvent.ExpansionCompleted,
                    is GameEvent.HqUpgraded, is GameEvent.FrenzyStarted, is GameEvent.TradeExecuted, is GameEvent.PropertyBought,
                    is GameEvent.PropertySold, is GameEvent.LuxuryBought -> HapticFeedbackType.Confirm
                    is GameEvent.PaymentMissed, is GameEvent.Foreclosure -> HapticFeedbackType.Reject
                    is GameEvent.ShortCovered -> if (e.forced) HapticFeedbackType.Reject else HapticFeedbackType.Confirm
                    else -> null
                }
                if (type != null) haptics.performHapticFeedback(type)
            }
        }

        val glassState = rememberHazeState()
        CompositionLocalProvider(LocalGlassState provides glassState) {
        Box(Modifier.fillMaxSize()) {
            // The ambient backdrop sits behind every screen and is what the glass refracts.
            AmbientBackdrop()
            when (val st = status) {
                GameSession.Status.Loading -> LoadingScreen()
                is GameSession.Status.Recovery -> RecoveryScreen(errors = st.errors, onStartOver = vm::startOver)
                is GameSession.Status.NeedsUpdate -> RecoveryScreen(
                    errors = listOf("This save was made by a newer version of the game (schema ${st.schema})."),
                    title = "Please update the game",
                    onStartOver = null,
                )
                is GameSession.Status.Failed -> RecoveryScreen(errors = listOf(st.message), title = "Something went wrong", onStartOver = null)
                GameSession.Status.Ready -> ReadyContent(vm)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp)) { data ->
                Box(
                    Modifier.padding(horizontal = Space.gutter).glass(AurumShapes.pill, GlassWeight.Thick).padding(horizontal = Space.xl, vertical = Space.md),
                ) { Text(data.visuals.message, style = Aurum.type.body, color = Aurum.colors.textPrimary) }
            }
        }
        }
    }
}

@Composable
private fun ReadyContent(vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    // After an update: the release's highlights (shown once, after any Daily Ledger), or a short message.
    var whatsNew by remember { mutableStateOf<Pair<String, List<com.arnavgames.tycoon.ui.common.Highlight>>?>(null) }
    LaunchedEffect(Unit) {
        val from = kotlinx.coroutines.withTimeoutOrNull(5_000) { vm.updates.updatedFrom.filterNotNull().first() } ?: return@LaunchedEffect
        vm.updates.consumeUpdatedFrom()
        val version = com.arnavgames.tycoon.BuildConfig.VERSION_NAME
        val highlights = com.arnavgames.tycoon.ui.common.WhatsNew.forUpdate(from, version)
        if (highlights.isNotEmpty()) whatsNew = version to highlights
        else vm.say("Updated from $from to $version. Your game is safe.")
    }
    val s = state ?: return LoadingScreen()
    com.arnavgames.tycoon.notify.NotifyOffer(vm)
    val needsWelcome = s.progress.onboardingStep == 0 && s.businesses.isEmpty()

    AnimatedContent(targetState = needsWelcome, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }, label = "welcome") { welcome ->
        if (welcome) {
            WelcomeScreen(onBegin = {
                vm.dispatch(Command.OpenBusiness("retail")) { vm.dispatch(Command.AdvanceOnboarding(1)) }
            })
        } else {
            val nav = rememberNavController()
            val reduced = Aurum.reducedMotion
            val enter: EnterTransition = if (reduced) fadeIn(tween(0)) else slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))
            val exit: ExitTransition = if (reduced) fadeOut(tween(0)) else slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(200))
            val popEnter: EnterTransition = if (reduced) fadeIn(tween(0)) else slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))
            val popExit: ExitTransition = if (reduced) fadeOut(tween(0)) else slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200))
            NavHost(
                nav, startDestination = MainRoute,
                enterTransition = { enter }, exitTransition = { exit }, popEnterTransition = { popEnter }, popExitTransition = { popExit },
            ) {
                composable<MainRoute> {
                    MainShell(
                        vm = vm,
                        onOpenBusiness = { nav.navigate(BusinessRoute(it)) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        onOpenCounter = { nav.navigate(CounterRoute) },
                        onOpenCredits = { nav.navigate(CreditsRoute) },
                        onOpenInstrument = { nav.navigate(InstrumentRoute(it)) },
                        onOpenNews = { nav.navigate(NewsRoute) },
                        onOpenCity = { nav.navigate(CityRoute(it)) },
                        onOpenProperty = { nav.navigate(PropertyRoute(it)) },
                        onOpenLuxuryCategory = { nav.navigate(LuxuryCategoryRoute(it)) },
                        onOpenAuction = { nav.navigate(AuctionRoute) },
                        onOpenDesk = { nav.navigate(DeskRoute) },
                        onOpenFortune = { nav.navigate(FortuneRoute) },
                        onOpenFinance = { nav.navigate(FinanceRoute) },
                        onOpenAchievements = { nav.navigate(AchievementsRoute) },
                        onOpenCrown = { nav.navigate(CrownRoute) },
                        onOpenStore = { nav.navigate(StoreRoute) },
                        onOpenAccount = { nav.navigate(AccountRoute) },
                        onOpenSocial = { nav.navigate(SocialRoute) },
                        onOpenTiming = { nav.navigate(TimingRoute) },
                    )
                }
                composable<TimingRoute> { com.arnavgames.tycoon.ui.markets.MarketTimingScreen(vm, onBack = { nav.popBackStack() }) }
                composable<SocialRoute> { com.arnavgames.tycoon.ui.social.SocialScreen(vm, onBack = { nav.popBackStack() }, onOpenAccount = { nav.navigate(AccountRoute) }) }
                composable<AccountRoute> { com.arnavgames.tycoon.ui.account.AccountScreen(vm, onBack = { nav.popBackStack() }) }
                composable<StoreRoute> { com.arnavgames.tycoon.ui.store.StoreScreen(vm, onBack = { nav.popBackStack() }, onOpenGoals = { vm.requestTab("dynasty"); nav.popBackStack(MainRoute, inclusive = false) }) }
                composable<AchievementsRoute> { com.arnavgames.tycoon.ui.dynasty.AchievementsScreen(vm, onBack = { nav.popBackStack() }) }
                composable<CrownRoute> { com.arnavgames.tycoon.ui.dynasty.CrownScreen(vm, onBack = { nav.popBackStack() }) }
                composable<DeskRoute> { com.arnavgames.tycoon.ui.world.DeskScreen(vm, onBack = { nav.popBackStack() }, onOpenRival = { nav.navigate(RivalRoute(it)) }) }
                composable<FortuneRoute> { com.arnavgames.tycoon.ui.world.FortuneScreen(vm, onBack = { nav.popBackStack() }, onOpenRival = { nav.navigate(RivalRoute(it)) }) }
                composable<RivalRoute> { entry -> com.arnavgames.tycoon.ui.world.RivalScreen(vm, entry.toRoute<RivalRoute>().id, onBack = { nav.popBackStack() }) }
                composable<FinanceRoute> { com.arnavgames.tycoon.ui.world.FinanceScreen(vm, onBack = { nav.popBackStack() }, onOpenTaxes = { nav.navigate(TaxRoute) }) }
                composable<TaxRoute> { com.arnavgames.tycoon.ui.world.TaxFilingScreen(vm, onBack = { nav.popBackStack() }) }
                composable<LuxuryCategoryRoute> { entry ->
                    com.arnavgames.tycoon.ui.collection.CategoryScreen(vm, entry.toRoute<LuxuryCategoryRoute>().id, onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(LuxuryItemRoute(it)) })
                }
                composable<LuxuryItemRoute> { entry ->
                    com.arnavgames.tycoon.ui.collection.ItemScreen(vm, entry.toRoute<LuxuryItemRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<AuctionRoute> { com.arnavgames.tycoon.ui.collection.AuctionScreen(vm, onBack = { nav.popBackStack() }) }
                composable<CityRoute> { entry ->
                    com.arnavgames.tycoon.ui.estates.CityScreen(vm, entry.toRoute<CityRoute>().id, onBack = { nav.popBackStack() }, onOpenDistrict = { nav.navigate(DistrictRoute(it)) })
                }
                composable<DistrictRoute> { entry ->
                    com.arnavgames.tycoon.ui.estates.DistrictScreen(vm, entry.toRoute<DistrictRoute>().id, onBack = { nav.popBackStack() }, onOpenProperty = { nav.navigate(PropertyRoute(it)) })
                }
                composable<PropertyRoute> { entry ->
                    com.arnavgames.tycoon.ui.estates.PropertyScreen(vm, entry.toRoute<PropertyRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<InstrumentRoute> { entry ->
                    com.arnavgames.tycoon.ui.markets.InstrumentScreen(vm = vm, instrumentId = entry.toRoute<InstrumentRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<NewsRoute> {
                    com.arnavgames.tycoon.ui.markets.NewsScreen(vm = vm, onBack = { nav.popBackStack() }, onOpenInstrument = { nav.navigate(InstrumentRoute(it)) })
                }
                composable<BusinessRoute> { entry ->
                    BusinessDetailScreen(vm = vm, businessId = entry.toRoute<BusinessRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<CounterRoute> { com.arnavgames.tycoon.ui.empire.CounterScreen(vm, onBack = { nav.popBackStack() }) }
                composable<SettingsRoute> { SettingsScreen(vm = vm, onBack = { nav.popBackStack() }, onCredits = { nav.navigate(CreditsRoute) }) }
                composable<CreditsRoute> { CreditsScreen(onBack = { nav.popBackStack() }, artCredits = vm.artCreditLines(), portraitCount = vm.portraitCount() + vm.rivalPortraitCount()) }
            }
        }
    }
    offline?.let { report -> OfflineEarningsDialog(report = report, onDismiss = vm::dismissOffline) }
    if (offline == null) whatsNew?.let { (v, h) -> com.arnavgames.tycoon.ui.common.WhatsNewSheet(v, h) { whatsNew = null } }
}
