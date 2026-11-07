package com.arnavgames.tycoon.game

import android.content.Context
import com.arnavgames.tycoon.cloud.CloudService
import com.arnavgames.tycoon.cloud.SyncResult
import com.arnavgames.tycoon.data.settings.SettingsStore
import com.arnavgames.tycoon.update.UpdateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide services that outlive any screen: the cloud account and save, and automatic updates. The app lifecycle
 * drives them ([onForeground], [onBackground]); screens read them through the view model.
 */
@Singleton
class AppServices @Inject constructor(
    @ApplicationContext private val app: Context,
    private val session: GameSession,
    settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    val cloud: CloudService by lazy { CloudService(app, session) }
    val updates: UpdateManager by lazy { UpdateManager(app, session, settings, scope) }
    val music: com.arnavgames.tycoon.audio.MusicPlayer by lazy { com.arnavgames.tycoon.audio.MusicPlayer(app, scope) }

    /** A background sync found that both this device and the cloud changed: the Account screen asks which to keep. */
    private val _cloudConflict = MutableStateFlow(false)
    val cloudConflict: StateFlow<Boolean> = _cloudConflict.asStateFlow()
    fun clearCloudConflict() { _cloudConflict.value = false }

    private var lastAutoSync = 0L

    fun onForeground() {
        music.onForeground()
        updates.refreshPermission()
        updates.onForeground()
    }

    /**
     * Leaving the game: back it up to the cloud (signed-in players, at most every 15 minutes; never overwrites a newer
     * cloud copy) and then let the updater install a ready update. Both run after the game has been saved.
     */
    fun onBackground() {
        music.onBackground()
        scope.launch {
            session.saveNow()
            val now = System.currentTimeMillis()
            if (cloud.available && cloud.user.value != null && now - lastAutoSync > AUTO_SYNC_MS && cloud.localDirty()) {
                lastAutoSync = now
                if (cloud.sync() is SyncResult.Conflict) _cloudConflict.value = true
            }
            updates.onBackground()
        }
    }

    companion object {
        const val AUTO_SYNC_MS = 15 * 60_000L
    }
}
