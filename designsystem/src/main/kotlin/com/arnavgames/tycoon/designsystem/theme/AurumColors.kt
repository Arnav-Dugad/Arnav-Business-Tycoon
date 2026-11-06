package com.arnavgames.tycoon.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurum Glass color tokens. Every color used by the game comes from here; screens never hardcode colors.
 * Text-token contrast against the opaque surfaces is checked by tools/scripts/check_contrast.py (WCAG AA).
 * Glass tokens are translucent and always sit on top of the ambient backdrop.
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
    // ---- Glass ----
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStrokeTop: Color,
    val glassStrokeBottom: Color,
    val glassSheen: Color,
    val separator: Color,
    /** Fill behind the selected item of tab bars and similar controls. */
    val selectionFill: Color,
    /** Gold fill for buttons and highlighted tiles (dark text sits on it). */
    val goldFillTop: Color,
    val goldFillBottom: Color,
    // ---- Ambient backdrop ----
    val ambientTop: Color,
    val ambientBottom: Color,
    val orbGold: Color,
    val orbCool: Color,
    val orbWarm: Color,
) {
    /** Metallic gold used for primary buttons, the crest and celebration moments. */
    val goldGradient: Brush get() = Brush.linearGradient(listOf(goldBright, gold, goldDeep))

    /** Gold fill for primary controls. */
    val goldFill: Brush get() = Brush.verticalGradient(listOf(goldFillTop, goldFillBottom))

    /** Edge light for glass: bright along the top, almost invisible at the bottom. */
    val glassStroke: Brush get() = Brush.verticalGradient(listOf(glassStrokeTop, glassStrokeBottom))

    /** Soft vertical sheen inside glass surfaces. */
    val glassSheenBrush: Brush get() = Brush.verticalGradient(listOf(glassSheen, Color.Transparent))
}

val AurumDark = AurumColors(
    isDark = true,
    background = Color(0xFF050506),
    surface = Color(0xFF141416),
    surfaceRaised = Color(0xFF1C1C1F),
    surfaceHigh = Color(0xFF26262A),
    outline = Color(0x1FFFFFFF),
    outlineStrong = Color(0x38FFFFFF),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFFB3B3B8),
    textTertiary = Color(0xFF98989F),
    textOnGold = Color(0xFF1A1407),
    gold = Color(0xFFD9B26F),
    goldBright = Color(0xFFF2D49A),
    goldDeep = Color(0xFFA27A38),
    goldText = Color(0xFFE6C487),
    gain = Color(0xFF34D35C),
    loss = Color(0xFFFF5A52),
    warning = Color(0xFFFFCC33),
    info = Color(0xFF6CCBFF),
    bullion = Color(0xFFEBC66C),
    scrim = Color(0x99000000),
    glassFill = Color(0x17FFFFFF),
    glassFillStrong = Color(0x29FFFFFF),
    glassStrokeTop = Color(0x40FFFFFF),
    glassStrokeBottom = Color(0x0DFFFFFF),
    glassSheen = Color(0x14FFFFFF),
    separator = Color(0x1AFFFFFF),
    selectionFill = Color(0x29FFFFFF),
    goldFillTop = Color(0xFFF2D49A),
    goldFillBottom = Color(0xFFD9B26F),
    ambientTop = Color(0xFF0B0B10),
    ambientBottom = Color(0xFF050506),
    orbGold = Color(0x5CC8A15A),
    orbCool = Color(0x473A66B8),
    orbWarm = Color(0x38B04E66),
)

val AurumLight = AurumColors(
    isDark = false,
    background = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEAE7E1),
    outline = Color(0x1A000000),
    outlineStrong = Color(0x2E000000),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF55555A),
    textTertiary = Color(0xFF636369),
    textOnGold = Color(0xFF1A1407),
    gold = Color(0xFFA47A33),
    goldBright = Color(0xFFD2AC63),
    goldDeep = Color(0xFF8E6A27),
    goldText = Color(0xFF775719),
    gain = Color(0xFF17702E),
    loss = Color(0xFFC4262E),
    warning = Color(0xFF855400),
    info = Color(0xFF0B5EC2),
    bullion = Color(0xFF9C7420),
    scrim = Color(0x66000000),
    glassFill = Color(0x9EFFFFFF),
    glassFillStrong = Color(0xC7FFFFFF),
    glassStrokeTop = Color(0xF2FFFFFF),
    glassStrokeBottom = Color(0x14000000),
    glassSheen = Color(0x66FFFFFF),
    separator = Color(0x17000000),
    selectionFill = Color(0x12000000),
    goldFillTop = Color(0xFFF0D293),
    goldFillBottom = Color(0xFFD3AC62),
    ambientTop = Color(0xFFF7F4EF),
    ambientBottom = Color(0xFFEEF0F5),
    orbGold = Color(0x66EBC27A),
    orbCool = Color(0x4D8EB7E8),
    orbWarm = Color(0x40F0A99A),
)

val LocalAurumColors = staticCompositionLocalOf { AurumDark }
