package com.arnavgames.tycoon.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.arnavgames.tycoon.designsystem.theme.Aurum
import java.util.concurrent.ConcurrentHashMap

/**
 * The game's illustrations (docs/ART_PIPELINE.md §3b): one painted look across businesses (and each of their tiers and
 * expansions), cities and districts, property types, towers, HQ levels, chapters, collection categories and pieces,
 * and world events. All bundled in the APK; paths are fixed by id. [pick] returns the first path that exists, so a
 * missing image always falls back to a broader one (a tier to its business, a piece to its category).
 */
object Art {
    fun business(id: String) = "img/business/$id.webp"
    /** The painting for a business at [tiersReached] tiers (0 = the starter business). */
    fun tier(businessId: String, tiersReached: Int) = if (tiersReached <= 0) business(businessId) else "img/tier/${businessId}_$tiersReached.webp"
    fun expansion(businessId: String, expansionId: String) = "img/expansion/${businessId}_$expansionId.webp"
    fun city(id: String) = "img/city/$id.webp"
    fun district(id: String) = "img/district/$id.webp"
    fun property(type: String) = "img/property/$type.webp"
    fun tower(id: String) = "img/tower/$id.webp"
    fun hq(level: Int) = "img/hq/$level.webp"
    fun chapter(n: Int) = "img/chapter/${n.coerceIn(1, 8)}.webp"
    fun luxuryCategory(id: String) = "img/luxurycat/$id.webp"
    fun luxuryItem(id: String) = "img/luxury/$id.webp"
    fun event(id: String) = "img/event/$id.webp"
    const val WELCOME = "img/scene/welcome.webp"

    private val folders = ConcurrentHashMap<String, Set<String>>()

    /** Whether the asset exists (folder listings are cached). */
    fun has(context: Context, path: String): Boolean {
        val dir = path.substringBeforeLast('/')
        val files = folders.getOrPut(dir) { runCatching { context.assets.list(dir)?.toSet() }.getOrNull().orEmpty() }
        return path.substringAfterLast('/') in files
    }

    /** The first of [paths] that exists, or the last one. */
    fun pick(context: Context, vararg paths: String): String = paths.firstOrNull { has(context, it) } ?: paths.last()
}

/** An illustration, cropped to fill; a soft placeholder shows while it decodes. [scrim] darkens the bottom for text. */
@Composable
fun Illustration(path: String, modifier: Modifier = Modifier, scrim: Boolean = false, fallback: String? = null, content: @Composable BoxScope.() -> Unit = {}) {
    val ctx = LocalContext.current
    val resolved = remember(path, fallback) { if (fallback != null) Art.pick(ctx, path, fallback) else path }
    Box(modifier.background(Aurum.colors.separator)) {
        AsyncImage(model = "file:///android_asset/$resolved", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (scrim) Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))))
        content()
    }
}
