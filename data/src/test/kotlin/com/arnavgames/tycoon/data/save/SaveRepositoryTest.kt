package com.arnavgames.tycoon.data.save

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arnavgames.tycoon.core.content.ContentLoader
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.time.FakeClock
import com.arnavgames.tycoon.data.db.TycoonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SaveRepositoryTest {
    private lateinit var db: TycoonDatabase
    private lateinit var repo: SaveRepository
    private val loaded = ContentLoader.loadFromDirectory(File("../content"))
    private val engine = GameEngine(loaded.pack)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TycoonDatabase::class.java).allowMainThreadQueries().build()
        repo = SaveRepository(db.saveDao(), Dispatchers.Unconfined, deviceId = { "test-device" }, nowWall = { 1_800_000_000_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun game(taps: Int): GameState {
        var s = engine.newGame(1, loaded.hash, FakeClock())
        repeat(taps) { s = (engine.apply(s, Command.HustleTap()) as CommandResult.Ok).state }
        return s
    }

    @Test
    fun roundTripAndRevisionsIncrease() = runBlocking {
        assertEquals(SaveRepository.LoadResult.Empty, repo.load())
        val r1 = repo.save(game(1), 0.0)
        val r2 = repo.save(game(2), 0.0)
        assertEquals(r1 + 1, r2)
        val result = repo.load() as SaveRepository.LoadResult.Loaded
        assertEquals(game(2), result.save.state)
        assertEquals(0, result.recoveredFromOlder)
    }

    @Test
    fun keepsOnlyTheNewestFiveSnapshots() = runBlocking {
        repeat(9) { repo.save(game(it + 1), 0.0) }
        assertEquals(SaveRepository.KEEP, db.saveDao().latest(0, 100).size)
        assertEquals(9L, db.saveDao().maxRevision(0))
    }

    @Test
    fun corruptedNewestFallsBackToPreviousAndIsQuarantined() = runBlocking {
        repo.save(game(3), 0.0)
        repo.save(game(4), 0.0)
        val newest = db.saveDao().latest(0, 1).single()
        val broken = newest.blob.copyOf().also { it[it.size - 5] = (it[it.size - 5].toInt() xor 0x33).toByte() }
        db.saveDao().delete(newest.id)
        db.saveDao().insert(newest.copy(id = 0, blob = broken))
        val result = repo.load() as SaveRepository.LoadResult.Loaded
        assertEquals(game(3), result.save.state)
        assertEquals(1, result.recoveredFromOlder)
        assertTrue(result.errors.single().contains("checksum"))
        assertEquals(1, db.saveDao().quarantined().size)
        assertTrue(repo.exportQuarantined()!!.contentEquals(broken))
    }

    @Test
    fun allCorruptIsReportedNotReplacedWithANewGame() = runBlocking {
        repo.save(game(1), 0.0)
        val snap = db.saveDao().latest(0, 1).single()
        db.saveDao().delete(snap.id)
        db.saveDao().insert(snap.copy(id = 0, blob = "garbage".toByteArray()))
        val result = repo.load()
        assertTrue(result is SaveRepository.LoadResult.Unrecoverable)
        assertEquals(1, db.saveDao().quarantined().size)
    }

    @Test
    fun importValidatesBeforeStoring() = runBlocking {
        repo.save(game(5), 0.0)
        val bytes = repo.exportLatest()!!
        val imported = repo.import(bytes)
        assertEquals(game(5), imported.state)
        val bad = runCatching { repo.import("nope".toByteArray()) }
        assertTrue(bad.isFailure)
        assertEquals(2, db.saveDao().latest(0, 10).size)
    }
}
