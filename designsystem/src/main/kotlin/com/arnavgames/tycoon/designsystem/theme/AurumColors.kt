package com.arnavgames.tycoon.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurum color tokens. Every color used by the game comes from here; screens never hardcode colors.
 * Contrast of text tokens against [background] and [surface] is checked by tools/scripts/check_contrast.py
 * (WCAG AA: >= 4.5 for body text, >= 3.0 for large text / icons).
 */
@Immutable
data class AurumColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnGold: Color,
    val gold: Color,
    val goldBright: Color,
    val goldDeep: Color,
    val goldText: Color,
    val gain: Color,
    val loss: Color,
    val warning: Color,
    val info: Color,
    val bullion: Color,
    val scrim: Color,
) {
    /** Metallic gold used for primary buttons, the crown mark and celebration moments. */
    val goldGradient: Brush
        get() = Brush.linearGradient(listOf(goldBright, gold, goldDeep))

    /** Subtle vertical wash behind hero areas. */
    val heroWash: Brush
        get() = Brush.verticalGradient(listOf(surfaceRaised, background))
}

val AurumDark = AurumColors(
    isDark = true,
    background = Color(0xFF0B0C0F),
    surface = Color(0xFF13151A),
    surfaceRaised = Color(0xFF1A1D24),
    surfaceHigh = Color(0xFF232731),
    outline = Color(0xFF2A2F3A),
    outlineStrong = Color(0xFF3B4252),
    textPrimary = Color(0xFFF4EFE6),
    textSecondary = Color(0xFFB9B2A5),
    textTertiary = Color(0xFF9D978A),
    textOnGold = Color(0xFF1A1407),
    gold = Color(0xFFD9B26F),
    goldBright = Color(0xFFF2D49A),
    goldDeep = Color(0xFFA27A38),
    goldText = Color(0xFFE2BE7E),
    gain = Color(0xFF41D691),
    loss = Color(0xFFF26A80),
    warning = Color(0xFFF2B451),
    info = Color(0xFF86B6F2),
    bullion = Color(0xFFEBC66C),
    scrim = Color(0xCC000000),
)

val AurumLight = AurumColors(
    isDark = false,
    background = Color(0xFFF6F2EA),
    surface = Color(0xFFFFFDF8),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEFE8DA),
    outline = Color(0xFFE0D6C4),
    outlineStrong = Color(0xFFC8BBA3),
    textPrimary = Color(0xFF16171B),
    textSecondary = Color(0xFF4D4942),
    textTertiary = Color(0xFF6E685E),
    textOnGold = Color(0xFF1A1407),
    gold = Color(0xFFA47A33),
    goldBright = Color(0xFFD2AC63),
    goldDeep = Color(0xFF8E6A27),
    goldText = Color(0xFF7A5A1E),
    gain = Color(0xFF0B6E42),
    loss = Color(0xFFB72C47),
    warning = Color(0xFF855400),
    info = Color(0xFF2D63A8),
    bullion = Color(0xFF9C7420),
    scrim = Color(0x99000000),
)

val LocalAurumColors = staticCompositionLocalOf { AurumDark }
