package com.arnavgames.tycoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.arnavgames.tycoon.designsystem.theme.Aurum
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arnavgames.tycoon.designsystem.theme.AurumTheme
import com.arnavgames.tycoon.designsystem.theme.ThemeMode
import com.arnavgames.tycoon.ui.foundation.FoundationScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // Debug/test hook so screenshots of both themes can be captured deterministically.
        val forced = intent?.getStringExtra(EXTRA_THEME)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        setContent {
            var mode by rememberSaveable { mutableStateOf(forced ?: ThemeMode.System) }
            AurumTheme(mode = mode) {
                // System bar icons follow the in-game theme, not the phone's setting.
                val dark = Aurum.colors.isDark
                LaunchedEffect(dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                FoundationScreen(
                    themeMode = mode,
                    onToggleTheme = {
                        mode = when (mode) {
                            ThemeMode.Light -> ThemeMode.Dark
                            ThemeMode.Dark -> ThemeMode.Light
                            ThemeMode.System -> if (resources.configuration.isNightModeActive) ThemeMode.Light else ThemeMode.Dark
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_THEME = "abt.theme"
    }
}
