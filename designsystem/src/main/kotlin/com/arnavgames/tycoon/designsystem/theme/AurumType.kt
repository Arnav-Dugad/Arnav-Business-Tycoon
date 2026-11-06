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

/** Inter (UI + numbers) and Fraunces (display), both SIL OFL 1.1. Built by tools/fonts/build_fonts.py. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Fraunces = FontFamily(
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val TrimBoth = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

@Immutable
data class AurumTypography(
    val hero: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
    /** Tabular figures so rolling counters never jitter horizontally. */
    val numberHero: TextStyle,
    val number: TextStyle,
    val numberSmall: TextStyle,
)

val DefaultAurumTypography = AurumTypography(
    hero = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.01).em, lineHeightStyle = TrimBoth),
    display = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.005).em),
    headline = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em),
    numberHero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum, lnum", letterSpacing = (-0.02).em),
    number = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum, lnum"),
    numberSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum, lnum"),
)

val LocalAurumTypography = staticCompositionLocalOf { DefaultAurumTypography }
