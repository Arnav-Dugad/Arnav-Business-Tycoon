package com.arnavgames.tycoon.data

import android.content.Context
import androidx.room.Room
import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.time.GameClock
import com.arnavgames.tycoon.data.db.SaveDao
import com.arnavgames.tycoon.data.db.TycoonDatabase
import com.arnavgames.tycoon.data.save.SaveRepository
import com.arnavgames.tycoon.data.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): TycoonDatabase =
        Room.databaseBuilder(ctx, TycoonDatabase::class.java, TycoonDatabase.NAME)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun saveDao(db: TycoonDatabase): SaveDao = db.saveDao()

    @Provides @Singleton
    fun settings(@ApplicationContext ctx: Context) = SettingsStore(ctx)

    @Provides @Singleton
    fun clock(@ApplicationContext ctx: Context): GameClock = SystemGameClock(ctx)

    @Provides @Singleton
    fun saveRepository(dao: SaveDao, settings: SettingsStore, clock: GameClock) =
        SaveRepository(dao, Dispatchers.IO, deviceId = { settings.deviceId() }, nowWall = { clock.wallMillis() })

    /** Game content from APK assets (the repo's /content directory is packaged as assets). */
    @Provides @Singleton
    fun content(@ApplicationContext ctx: Context): ContentLoader.Loaded =
        ContentLoader.load { path -> ctx.assets.open(path).bufferedReader().use { it.readText() } }
}
