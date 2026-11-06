package com.arnavgames.tycoon.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemePref { System, Dark, Light }
enum class NotationPref { Suffix, Scientific }

data class Settings(
    val theme: ThemePref = ThemePref.System,
    val reducedMotion: Boolean = false,
    val haptics: Boolean = true,
    val musicVolume: Float = 0.6f,
    val sfxVolume: Float = 0.8f,
    val notation: NotationPref = NotationPref.Suffix,
)

/** Player preferences (DataStore). Game progress is never stored here — only in saves. */
class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val haptics = booleanPreferencesKey("haptics")
        val music = floatPreferencesKey("music_volume")
        val sfx = floatPreferencesKey("sfx_volume")
        val notation = stringPreferencesKey("notation")
        val deviceId = stringPreferencesKey("device_id")
    }

    private fun fromPrefs(p: Preferences) = Settings(
        theme = p[K.theme]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.System,
        reducedMotion = p[K.reducedMotion] ?: false,
        haptics = p[K.haptics] ?: true,
        musicVolume = p[K.music] ?: 0.6f,
        sfxVolume = p[K.sfx] ?: 0.8f,
        notation = p[K.notation]?.let { runCatching { NotationPref.valueOf(it) }.getOrNull() } ?: NotationPref.Suffix,
    )

    val settings: Flow<Settings> = context.dataStore.data.map(::fromPrefs)

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = fromPrefs(p)
            val n = transform(cur)
            p[K.theme] = n.theme.name
            p[K.reducedMotion] = n.reducedMotion
            p[K.haptics] = n.haptics
            p[K.music] = n.musicVolume.coerceIn(0f, 1f)
            p[K.sfx] = n.sfxVolume.coerceIn(0f, 1f)
            p[K.notation] = n.notation.name
        }
    }

    /** Random per-install id (not tied to the user or hardware) used to label saves for conflict screens. */
    suspend fun deviceId(): String {
        context.dataStore.data.first()[K.deviceId]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { if (it[K.deviceId] == null) it[K.deviceId] = id }
        return context.dataStore.data.first()[K.deviceId] ?: id
    }
}
