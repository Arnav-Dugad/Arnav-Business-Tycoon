package com.arnavgames.tycoon.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.arnavgames.tycoon.designsystem.theme.Aurum

/**
 * The game's illustrations (docs/ART_PIPELINE.md §3b): one painted look across businesses, cities, property types,
 * chapters and collection categories. All bundled in the APK; paths are fixed by id.
 */
object Art {
    fun business(id: String) = "img/business/$id.webp"
    fun city(id: String) = "img/city/$id.webp"
    fun property(type: String) = "img/property/$type.webp"
    fun chapter(n: Int) = "img/chapter/${n.coerceIn(1, 8)}.webp"
    fun luxuryCategory(id: String) = "img/luxurycat/$id.webp"
    const val WELCOME = "img/scene/welcome.webp"
}

/** An illustration, cropped to fill; a soft placeholder shows while it decodes. [scrim] darkens the bottom for text. */
@Composable
fun Illustration(path: String, modifier: Modifier = Modifier, scrim: Boolean = false, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.background(Aurum.colors.separator)) {
        AsyncImage(model = "file:///android_asset/$path", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (scrim) Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))))
        content()
    }
}
