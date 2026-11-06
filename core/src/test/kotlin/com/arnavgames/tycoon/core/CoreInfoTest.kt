package com.arnavgames.tycoon.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreInfoTest {
    @Test
    fun schemaVersionIsPositive() {
        assertTrue(CoreInfo.SAVE_SCHEMA_VERSION >= 1)
    }
}
