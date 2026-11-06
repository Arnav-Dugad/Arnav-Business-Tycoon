package com.arnavgames.tycoon.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.arnavgames.tycoon.designsystem.R

/**
 * Inter (SIL OFL 1.1) in two optical cuts, built by tools/fonts/build_fonts.py:
 * - InterText (opsz 14): UI copy, generous spacing for small sizes.
 * - InterDisplay (opsz 32): large titles and hero numbers, tighter and more refined.
 * The ramp follows the familiar large-title / headline / body / footnote rhythm of premium mobile UIs.
 */
val InterText = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

@Immutable
data class AurumTypography(
    /** Large title (34). */
    val hero: TextStyle,
    /** Title 1 (28). */
    val display: TextStyle,
    /** Title 2 (22). */
    val headline: TextStyle,
    /** Headline (17, semibold). */
    val title: TextStyle,
    /** Subheadline emphasis (15, semibold). */
    val titleSmall: TextStyle,
    /** Body (16). */
    val body: TextStyle,
    /** Footnote (13). */
    val bodySmall: TextStyle,
    /** Caption / button label (13, medium). */
    val label: TextStyle,
    /** Section header (12, semibold, tracked caps). */
    val overline: TextStyle,
    /** Tabular figures so rolling counters never jitter horizontally. */
    val numberHero: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
)

val DefaultAurumTypography = AurumTypography(
    hero = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.022).em, lineHeightStyle = Centered),
    display = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
    headline = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em),
    title = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em),
    titleSmall = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em),
    body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.005).em),
    bodySmall = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    label = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    overline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
    numberHero = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, fontFeatureSettings = "tnum, lnum", letterSpacing = (-0.03).em),
    number = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum, lnum", letterSpacing = (-0.01).em),
    numberSmall = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum, lnum"),
)

val LocalAurumTypography = staticCompositionLocalOf { DefaultAurumTypography }
