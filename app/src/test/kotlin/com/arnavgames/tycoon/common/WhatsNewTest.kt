package com.arnavgames.tycoon.common

import com.arnavgames.tycoon.ui.common.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    private val line12 = WhatsNew.forVersion("1.2.0")

    @Test
    fun skippingAheadStillShowsTheLinesHighlights() {
        assertTrue(line12.isNotEmpty())
        assertEquals(line12, WhatsNew.forUpdate("1.1.0", "1.2.1"))
        assertEquals(line12, WhatsNew.forUpdate("1.0.0", "1.2.0"))
    }

    @Test
    fun aPatchWithinTheLineShowsNothingTwice() {
        assertTrue(WhatsNew.forUpdate("1.2.0", "1.2.1").isEmpty())
        assertTrue(WhatsNew.forUpdate("1.2.1-debug", "1.2.2-debug").isEmpty())
    }
}
