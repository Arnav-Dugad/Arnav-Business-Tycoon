package com.arnavgames.tycoon.data.save

import com.arnavgames.tycoon.core.save.DecodedSave
import com.arnavgames.tycoon.core.save.SaveCodec
import com.arnavgames.tycoon.core.save.SaveError
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.data.db.SaveDao
import com.arnavgames.tycoon.data.db.SaveSnapshotEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local save storage with corruption recovery (docs/ARCHITECTURE.md §5).
 * Load order: newest snapshot → older snapshots (up to [KEEP]) → [LoadResult.Unrecoverable].
 * Broken snapshots are quarantined, never deleted, and a broken newest save is never silently replaced by a new game.
 */
class SaveRepository(
    private val dao: SaveDao,
    private val io: CoroutineDispatcher,
    private val deviceId: suspend () -> String,
    private val nowWall: () -> Long,
) {
    private val mutex = Mutex()

    sealed interface LoadResult {
        data object Empty : LoadResult
        data class Loaded(val save: DecodedSave, val revision: Long, val recoveredFromOlder: Int, val errors: List<String>) : LoadResult
        data class Unrecoverable(val errors: List<String>) : LoadResult
        /** The save comes from a newer app version: never overwrite it. */
        data class NeedsUpdate(val schema: Int) : LoadResult
    }

    suspend fun load(slot: Int = 0): LoadResult = withContext(io) {
        mutex.withLock {
            val snaps = dao.latest(slot, KEEP)
            if (snaps.isEmpty()) return@withLock LoadResult.Empty
            val errors = mutableListOf<String>()
            snaps.forEachIndexed { i, snap ->
                try {
                    val decoded = SaveCodec.decode(snap.blob)
                    return@withLock LoadResult.Loaded(decoded, snap.revision, i, errors)
                } catch (e: SaveError.TooNew) {
                    return@withLock LoadResult.NeedsUpdate(e.schema)
                } catch (e: Exception) {
                    errors += "rev ${snap.revision}: ${e.message}"
                    dao.moveToQuarantine(snap, e.message ?: e.javaClass.simpleName, nowWall())
                }
            }
            LoadResult.Unrecoverable(errors)
        }
    }

    /** Writes a new snapshot atomically and returns its revision. */
    suspend fun save(state: GameState, netWorthLog10: Double, slot: Int = 0): Long = withContext(io) {
        mutex.withLock {
            val revision = (dao.maxRevision(slot) ?: 0L) + 1
            val bytes = SaveCodec.encode(state, revision, deviceId(), nowWall(), netWorthLog10)
            val header = SaveCodec.readHeader(bytes)
            dao.insertAndPrune(
                SaveSnapshotEntity(slot = slot, revision = revision, schema = header.schema, savedAtWall = header.savedAtWall, sha256 = header.sha256, blob = bytes),
                KEEP,
            )
            revision
        }
    }

    /** Bytes of the newest snapshot for "Export save". */
    suspend fun exportLatest(slot: Int = 0): ByteArray? = withContext(io) { dao.latest(slot, 1).firstOrNull()?.blob }

    /** Bytes of quarantined (corrupted) saves, for the recovery screen's export. */
    suspend fun exportQuarantined(): ByteArray? = withContext(io) { dao.quarantined().firstOrNull()?.blob }

    /** Validates an imported file fully before storing it as the newest revision. */
    suspend fun import(bytes: ByteArray, slot: Int = 0): DecodedSave = withContext(io) {
        val decoded = SaveCodec.decode(bytes) // throws SaveError with a clear message if invalid
        mutex.withLock {
            val revision = (dao.maxRevision(slot) ?: 0L) + 1
            val reencoded = SaveCodec.encode(decoded.state, revision, deviceId(), nowWall(), decoded.header.netWorthLog10)
            val header = SaveCodec.readHeader(reencoded)
            dao.insertAndPrune(SaveSnapshotEntity(slot = slot, revision = revision, schema = header.schema, savedAtWall = header.savedAtWall, sha256 = header.sha256, blob = reencoded), KEEP)
        }
        decoded
    }

    companion object {
        const val KEEP = 5
    }
}
