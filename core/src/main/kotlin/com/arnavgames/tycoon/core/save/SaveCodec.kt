package com.arnavgames.tycoon.core.save

import com.arnavgames.tycoon.core.CoreInfo
import com.arnavgames.tycoon.core.state.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Save file format (docs/ARCHITECTURE.md §5):
 *
 * ```
 * "ABTSAVE1" (8 bytes) | header length (int32 BE) | header JSON (UTF-8) | gzip(payload JSON)
 * ```
 * The header carries the schema version, revision, device, timestamps and the SHA-256 of the gzip payload.
 * Decoding verifies magic → header → checksum → gunzip → parse → migrate → decode. Any failure is a typed error,
 * never a silent fresh start.
 */
@Serializable
data class SaveHeader(
    val schema: Int,
    val contentHash: String,
    val revision: Long,
    val deviceId: String,
    val createdAtWall: Long,
    val savedAtWall: Long,
    val sha256: String,
    val payloadBytes: Int,
    /** Summary for conflict screens without decoding the payload. */
    val netWorthLog10: Double,
    val chapter: Int,
    val playTimeMs: Long,
)

data class DecodedSave(val header: SaveHeader, val state: GameState, val migratedFrom: Int?)

sealed class SaveError(message: String) : Exception(message) {
    class BadMagic : SaveError("Not a save file")
    class Truncated : SaveError("Save file is truncated")
    class BadHeader(cause: String) : SaveError("Unreadable save header: $cause")
    class ChecksumMismatch : SaveError("Save checksum mismatch (corrupted)")
    class BadPayload(cause: String) : SaveError("Unreadable save data: $cause")
    class TooNew(val schema: Int) : SaveError("Save is from a newer version (schema $schema). Please update the app.")
    class MigrationFailed(val from: Int, cause: String) : SaveError("Could not upgrade save from schema $from: $cause")
}

object SaveCodec {
    private val MAGIC = "ABTSAVE1".toByteArray(Charsets.US_ASCII)
    private const val MAX_HEADER = 64 * 1024

    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        classDiscriminator = "type"
        allowSpecialFloatingPointValues = false
        // Map keys like Map<String, Int> stay plain JSON objects.
    }

    fun encode(
        state: GameState,
        revision: Long,
        deviceId: String,
        savedAtWall: Long,
        netWorthLog10: Double,
    ): ByteArray {
        val payloadJson = json.encodeToString(GameState.serializer(), state).toByteArray(Charsets.UTF_8)
        val gz = gzip(payloadJson)
        val header = SaveHeader(
            schema = state.schema,
            contentHash = state.contentHash,
            revision = revision,
            deviceId = deviceId,
            createdAtWall = state.createdAtWall,
            savedAtWall = savedAtWall,
            sha256 = sha256Hex(gz),
            payloadBytes = gz.size,
            netWorthLog10 = netWorthLog10,
            chapter = state.progress.chapter,
            playTimeMs = state.simTimeMs,
        )
        val headerBytes = json.encodeToString(SaveHeader.serializer(), header).toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(MAGIC.size + 4 + headerBytes.size + gz.size)
            .put(MAGIC).putInt(headerBytes.size).put(headerBytes).put(gz).array()
    }

    /** Reads only the header (cheap; for slot lists and conflict screens). */
    fun readHeader(bytes: ByteArray): SaveHeader {
        if (bytes.size < MAGIC.size + 4) throw SaveError.Truncated()
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw SaveError.BadMagic()
        val len = ByteBuffer.wrap(bytes, MAGIC.size, 4).int
        if (len <= 0 || len > MAX_HEADER || MAGIC.size + 4 + len > bytes.size) throw SaveError.Truncated()
        return try {
            json.decodeFromString(SaveHeader.serializer(), String(bytes, MAGIC.size + 4, len, Charsets.UTF_8))
        } catch (e: Exception) {
            throw SaveError.BadHeader(e.message ?: e.javaClass.simpleName)
        }
    }

    fun decode(bytes: ByteArray): DecodedSave {
        val header = readHeader(bytes)
        val start = MAGIC.size + 4 + ByteBuffer.wrap(bytes, MAGIC.size, 4).int
        val gz = bytes.copyOfRange(start, bytes.size)
        if (gz.size != header.payloadBytes) throw SaveError.Truncated()
        if (sha256Hex(gz) != header.sha256) throw SaveError.ChecksumMismatch()
        if (header.schema > CoreInfo.SAVE_SCHEMA_VERSION) throw SaveError.TooNew(header.schema)
        val tree: JsonObject = try {
            json.parseToJsonElement(String(gunzip(gz), Charsets.UTF_8)).jsonObject
        } catch (e: Exception) {
            throw SaveError.BadPayload(e.message ?: e.javaClass.simpleName)
        }
        val payloadSchema = tree["schema"]?.jsonPrimitive?.int ?: header.schema
        val migrated = SaveMigrator.migrate(tree, payloadSchema)
        val state = try {
            json.decodeFromJsonElement(GameState.serializer(), migrated)
        } catch (e: Exception) {
            throw SaveError.BadPayload(e.message ?: e.javaClass.simpleName)
        }
        return DecodedSave(header, state, if (payloadSchema < CoreInfo.SAVE_SCHEMA_VERSION) payloadSchema else null)
    }

    fun sha256Hex(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 4 + 64)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}

/**
 * Upgrades old save trees step by step: v1 → v2 → … → current. Each step is a pure JsonObject transform
 * with a golden fixture test (core/src/test/resources/saves/vN.abtsave).
 */
object SaveMigrator {
    /** steps[n] upgrades a schema-n tree to schema n+1. */
    private val steps: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        // Example for the future:
        // 1 to { t -> JsonObject(t + ("newField" to JsonPrimitive(0))).withSchema(2) },
    )

    fun migrate(tree: JsonObject, fromSchema: Int): JsonObject {
        var t = tree
        var v = fromSchema
        while (v < CoreInfo.SAVE_SCHEMA_VERSION) {
            val step = steps[v] ?: throw SaveError.MigrationFailed(v, "no migration step from schema $v")
            t = try { step(t) } catch (e: Exception) { throw SaveError.MigrationFailed(v, e.message ?: "error") }
            v++
        }
        return t
    }

    internal fun JsonObject.withSchema(n: Int) = JsonObject(this + ("schema" to JsonPrimitive(n)))
}
