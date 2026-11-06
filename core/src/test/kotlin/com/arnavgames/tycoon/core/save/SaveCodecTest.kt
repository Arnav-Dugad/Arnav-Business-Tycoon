package com.arnavgames.tycoon.core.save

import com.arnavgames.tycoon.core.CoreInfo
import com.arnavgames.tycoon.core.engine.EngineFixtures
import com.arnavgames.tycoon.core.engine.EngineFixtures.adv
import com.arnavgames.tycoon.core.engine.EngineFixtures.busyState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SaveCodecTest {
    private fun encode(s: com.arnavgames.tycoon.core.state.GameState, rev: Long = 1) =
        SaveCodec.encode(s, rev, "device-test", 1_800_000_000_000L, EngineFixtures.engine.economy.netWorth(s).log10Dollars())

    @Test
    fun roundTripIsExact() {
        val s = busyState().adv(3_600_000)
        val bytes = encode(s, rev = 42)
        val decoded = SaveCodec.decode(bytes)
        assertEquals(s, decoded.state)
        assertEquals(42, decoded.header.revision)
        assertNull(decoded.migratedFrom)
        assertTrue(bytes.size < 64 * 1024, "save is compact: ${bytes.size} bytes")
    }

    @Test
    fun flippedByteIsDetected() {
        val bytes = encode(busyState())
        val corrupted = bytes.copyOf().also { it[it.size - 10] = (it[it.size - 10].toInt() xor 0x5A).toByte() }
        assertThrows(SaveError.ChecksumMismatch::class.java) { SaveCodec.decode(corrupted) }
    }

    @Test
    fun truncatedAndForeignFilesAreRejected() {
        val bytes = encode(busyState())
        assertThrows(SaveError.Truncated::class.java) { SaveCodec.decode(bytes.copyOf(bytes.size - 100)) }
        assertThrows(SaveError.Truncated::class.java) { SaveCodec.decode(bytes.copyOf(6)) }
        assertThrows(SaveError.BadMagic::class.java) { SaveCodec.decode("hello world, not a save".toByteArray()) }
    }

    @Test
    fun saveFromNewerVersionIsRefusedNotTruncated() {
        val future = busyState().copy(schema = CoreInfo.SAVE_SCHEMA_VERSION + 1)
        assertThrows(SaveError.TooNew::class.java) { SaveCodec.decode(encode(future)) }
    }

    @Test
    fun headerIsReadableWithoutDecodingPayload() {
        val s = busyState()
        val h = SaveCodec.readHeader(encode(s, rev = 7))
        assertEquals(7, h.revision)
        assertEquals(s.progress.chapter, h.chapter)
        assertEquals(s.contentHash, h.contentHash)
    }

    /**
     * Golden fixtures: one save per released schema version must always load. Regenerate the current
     * version's fixture with -Dabt.writeFixtures=true (only when introducing a new schema version).
     */
    @Test
    fun goldenFixturesForEverySchemaLoad() {
        val dir = File("src/test/resources/saves")
        val current = File(dir, "v${CoreInfo.SAVE_SCHEMA_VERSION}.abtsave")
        if (System.getProperty("abt.writeFixtures") == "true") {
            dir.mkdirs()
            current.writeBytes(encode(busyState().adv(600_000)))
        }
        val fixtures = (1..CoreInfo.SAVE_SCHEMA_VERSION).map { File(dir, "v$it.abtsave") }
        fixtures.forEach { f ->
            assertTrue(f.exists(), "missing golden fixture ${f.path}")
            val decoded = SaveCodec.decode(f.readBytes())
            assertEquals(CoreInfo.SAVE_SCHEMA_VERSION, decoded.state.schema)
        }
    }
}
