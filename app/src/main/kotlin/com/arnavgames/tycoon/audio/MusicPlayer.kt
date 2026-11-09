package com.arnavgames.tycoon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.arnavgames.tycoon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Background music (tools/audio/gen_music.py: four original loops). One loop per area of the game, crossfaded when
 * the player changes tab; silent in the background, when the volume is Off, or when another app is already playing
 * music (the player's own music or podcast wins).
 */
class MusicPlayer(private val app: Context, private val scope: CoroutineScope) {
    private val tracks = mapOf(
        "empire" to R.raw.music_empire, "markets" to R.raw.music_markets,
        "estates" to R.raw.music_estates, "collection" to R.raw.music_estates, "dynasty" to R.raw.music_dynasty,
        "counter" to R.raw.music_empire, "more" to R.raw.music_dynasty,
    )
    private var player: MediaPlayer? = null
    private var currentTrack: Int? = null
    private var scene = "empire"
    private var volume = 0f
    private var foreground = false
    private var fade: Job? = null

    fun setScene(name: String) = main { scene = name; update() }
    fun setVolume(v: Float) = main { volume = v; player?.let { apply(it, level()) }; update() }
    fun onForeground() = main { foreground = true; update() }
    fun onBackground() = main { foreground = false; update() }

    private fun main(block: () -> Unit) { scope.launch(Dispatchers.Main) { block() } }

    private fun level() = volume * GAIN
    private fun apply(p: MediaPlayer, v: Float) = runCatching { p.setVolume(v, v) }

    private fun update() {
        val want = if (foreground && volume > 0f) tracks[scene] else null
        if (want == currentTrack) {
            player?.let { if (want != null && !it.isPlaying) runCatching { it.start() } }
            return
        }
        // Don't start over music the player is already listening to in another app.
        if (currentTrack == null && want != null && app.getSystemService(AudioManager::class.java)?.isMusicActive == true) return
        val old = player
        currentTrack = want
        player = want?.let { create(it) }
        fade?.cancel()
        fade = scope.launch(Dispatchers.Main) {
            val target = level()
            for (step in 1..FADE_STEPS) {
                val k = step / FADE_STEPS.toFloat()
                old?.let { apply(it, target * (1 - k)) }
                player?.let { apply(it, target * k) }
                delay(FADE_MS / FADE_STEPS)
            }
            old?.let { runCatching { it.stop() }; it.release() }
        }
    }

    private fun create(resId: Int): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            app.resources.openRawResourceFd(resId).use { fd -> setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            isLooping = true
            prepare()
            setVolume(0f, 0f)
            start()
        }
    }.onFailure { Log.w("MusicPlayer", "could not play music", it) }.getOrNull()

    companion object {
        /** Music sits under the sound effects. */
        private const val GAIN = 0.55f
        private const val FADE_MS = 700L
        private const val FADE_STEPS = 14
    }
}
