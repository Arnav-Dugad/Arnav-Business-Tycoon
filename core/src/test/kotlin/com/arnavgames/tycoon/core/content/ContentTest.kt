package com.arnavgames.tycoon.core.content

import com.arnavgames.tycoon.core.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentTest {
    private val loaded = ContentLoader.loadFromRepo()

    @Test
    fun repositoryContentIsValid() {
        assertEquals(emptyList<ContentIssue>(), loaded.issues)
    }

    @Test
    fun chapterOneHasItsBusinesses() {
        val ids = loaded.pack.businesses.map { it.id }
        assertTrue(ids.containsAll(listOf("retail", "restaurants", "logistics")))
        assertTrue(loaded.pack.business("retail").baseCost.isPositive, "balance values are tuned by the simulator; only sanity is asserted here")
        assertTrue(loaded.pack.chapters.first().missions.size >= 5)
    }

    @Test
    fun hashIsStable() {
        assertEquals(loaded.hash, ContentLoader.loadFromRepo().hash)
        assertEquals(16, loaded.hash.length)
    }

    @Test
    fun brandBlocklistMatchesWholeWordsOnly() {
        assertNotNull(ContentValidator.findBlockedBrand("Rolex Watches"))
        assertNotNull(ContentValidator.findBlockedBrand("a new Ferrari"))
        assertNull(ContentValidator.findBlockedBrand("Pineapple Express"))
        assertNull(ContentValidator.findBlockedBrand("Seashell Bistro"))
    }

    @Test
    fun validatorCatchesBrokenContent() {
        val pack = loaded.pack
        val bad = pack.copy(businesses = pack.businesses.map { b ->
            if (b.id == "retail") b.copy(name = "Rolex Boutique", storageSeconds = 1, managers = emptyList()) else b
        })
        val issues = ContentValidator.validate(bad).map { it.message }
        assertTrue(issues.any { it.contains("blocked real brand") }, issues.toString())
        assertTrue(issues.any { it.contains("storageSeconds") }, issues.toString())
        assertTrue(issues.any { it.contains("manager") }, issues.toString())
    }
}
