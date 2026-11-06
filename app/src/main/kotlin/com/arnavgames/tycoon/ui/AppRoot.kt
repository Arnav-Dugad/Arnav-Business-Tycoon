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

@Serializable object MainRoute
@Serializable data class BusinessRoute(val id: String)
@Serializable object SettingsRoute
@Serializable object CreditsRoute

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
        LaunchedEffect(settings.haptics) {
            if (!settings.haptics) return@LaunchedEffect
            vm.events.collect { e ->
                val type = when (e) {
                    is GameEvent.HustleEarned -> HapticFeedbackType.SegmentTick
                    is GameEvent.LevelsBought, is GameEvent.Collected -> HapticFeedbackType.ContextClick
                    is GameEvent.ManagerHired, is GameEvent.BusinessOpened, is GameEvent.MissionClaimed,
                    is GameEvent.TierReached, is GameEvent.ExpansionCompleted, is GameEvent.HqUpgraded,
                    is GameEvent.ChapterCompleted, is GameEvent.FrenzyStarted,
                    is GameEvent.ConglomerateFormed, is GameEvent.HoldingFormed -> HapticFeedbackType.Confirm
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
    val s = state ?: return LoadingScreen()
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
                        onOpenCredits = { nav.navigate(CreditsRoute) },
                    )
                }
                composable<BusinessRoute> { entry ->
                    BusinessDetailScreen(vm = vm, businessId = entry.toRoute<BusinessRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<SettingsRoute> { SettingsScreen(vm = vm, onBack = { nav.popBackStack() }, onCredits = { nav.navigate(CreditsRoute) }) }
                composable<CreditsRoute> { CreditsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
    offline?.let { report -> OfflineEarningsDialog(report = report, onDismiss = vm::dismissOffline) }
}
