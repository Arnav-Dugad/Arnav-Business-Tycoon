package com.arnavgames.tycoon.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** One saved snapshot. The newest few per slot are kept so a corrupted save can fall back to the previous one. */
@Entity(tableName = "save_snapshots", indices = [Index(value = ["slot", "revision"])])
data class SaveSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slot: Int,
    val revision: Long,
    val schema: Int,
    val savedAtWall: Long,
    val sha256: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val blob: ByteArray,
) {
    override fun equals(other: Any?) = other is SaveSnapshotEntity && id == other.id && revision == other.revision && sha256 == other.sha256
    override fun hashCode() = (id * 31 + revision).hashCode()
}

/** Saves that failed to load are moved here instead of being deleted, so the player can export them. */
@Entity(tableName = "quarantine")
data class QuarantineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slot: Int,
    val revision: Long,
    val reason: String,
    val quarantinedAtWall: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val blob: ByteArray,
) {
    override fun equals(other: Any?) = other is QuarantineEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Dao
interface SaveDao {
    @Insert
    suspend fun insert(e: SaveSnapshotEntity): Long

    @Query("SELECT * FROM save_snapshots WHERE slot = :slot ORDER BY revision DESC, id DESC LIMIT :limit")
    suspend fun latest(slot: Int, limit: Int): List<SaveSnapshotEntity>

    @Query("SELECT MAX(revision) FROM save_snapshots WHERE slot = :slot")
    suspend fun maxRevision(slot: Int): Long?

    @Query(
        "DELETE FROM save_snapshots WHERE slot = :slot AND id NOT IN " +
            "(SELECT id FROM save_snapshots WHERE slot = :slot ORDER BY revision DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(slot: Int, keep: Int)

    @Query("DELETE FROM save_snapshots WHERE id = :id")
    suspend fun delete(id: Long)

    /** Atomic: the new snapshot and the pruning commit together or not at all. */
    @Transaction
    suspend fun insertAndPrune(e: SaveSnapshotEntity, keep: Int): Long {
        val id = insert(e)
        prune(e.slot, keep)
        return id
    }

    @Insert
    suspend fun quarantine(e: QuarantineEntity): Long

    @Query("SELECT * FROM quarantine ORDER BY id DESC")
    suspend fun quarantined(): List<QuarantineEntity>

    /** Moves a broken snapshot to quarantine in one transaction. */
    @Transaction
    suspend fun moveToQuarantine(snapshot: SaveSnapshotEntity, reason: String, now: Long) {
        quarantine(QuarantineEntity(slot = snapshot.slot, revision = snapshot.revision, reason = reason, quarantinedAtWall = now, blob = snapshot.blob))
        delete(snapshot.id)
    }
}

@Database(entities = [SaveSnapshotEntity::class, QuarantineEntity::class], version = 1, exportSchema = true)
abstract class TycoonDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao

    companion object {
        const val NAME = "tycoon.db"
    }
}
