package com.arnavgames.tycoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arnavgames.tycoon.designsystem.theme.ThemeMode
import com.arnavgames.tycoon.game.GameSession
import com.arnavgames.tycoon.ui.AppRoot
import com.arnavgames.tycoon.ui.GameViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash until the save has loaded (usually a few hundred milliseconds).
        splash.setKeepOnScreenCondition { vm.status.value == GameSession.Status.Loading }
        enableEdgeToEdge()
        // Test hook so screenshots of both themes can be captured deterministically (debug and tests only).
        val forced = if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_THEME)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } else null
        if (BuildConfig.DEBUG && savedInstanceState == null) intent?.getStringExtra(EXTRA_SCENARIO)?.let { vm.debugScenario(it) }
        setContent {
            AppRoot(vm, forcedTheme = forced, onDarkChanged = { dark ->
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            })
        }
    }

    companion object {
        const val EXTRA_THEME = "abt.theme"
        const val EXTRA_SCENARIO = "abt.scenario"
    }
}
