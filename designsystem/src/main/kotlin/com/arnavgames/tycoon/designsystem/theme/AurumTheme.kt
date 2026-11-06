package com.arnavgames.tycoon.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale (4dp grid). */
@Immutable
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val huge: Dp = 48.dp
    /** Screen side gutter. */
    val gutter: Dp = 20.dp
    /** Minimum touch target (accessibility). */
    val touch: Dp = 48.dp
}

/** Continuous-corner shape scale (see [SmoothCornerShape]). Capsules use a true half-height radius. */
@Immutable
object AurumShapes {
    val tiny = SmoothCornerShape(8.dp)
    val small = SmoothCornerShape(14.dp)
    val medium = SmoothCornerShape(22.dp)
    val large = SmoothCornerShape(30.dp)
    val sheet = SmoothCornerShape(36.dp)
    val pill = RoundedCornerShape(percent = 50)
}

enum class ThemeMode { System, Dark, Light }

/** User motion preference; when true, decorative animations are replaced by instant changes. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun AurumTheme(
    mode: ThemeMode = ThemeMode.System,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val colors = if (dark) AurumDark else AurumLight
    // Material3 is used only for low-level primitives (text selection, ripples); all visuals come from Aurum tokens.
    val material = if (dark) {
        darkColorScheme(
            primary = colors.gold, onPrimary = colors.textOnGold, background = colors.background,
            onBackground = colors.textPrimary, surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textSecondary, outline = colors.outline,
            error = colors.loss,
        )
    } else {
        lightColorScheme(
            primary = colors.goldDeep, onPrimary = colors.surface, background = colors.background,
            onBackground = colors.textPrimary, surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textSecondary, outline = colors.outline,
            error = colors.loss,
        )
    }
    CompositionLocalProvider(
        LocalAurumColors provides colors,
        LocalAurumTypography provides DefaultAurumTypography,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/** Accessor: `Aurum.colors.gold`, `Aurum.type.title`. */
object Aurum {
    val colors: AurumColors
        @Composable @ReadOnlyComposable get() = LocalAurumColors.current
    val type: AurumTypography
        @Composable @ReadOnlyComposable get() = LocalAurumTypography.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
