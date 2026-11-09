package com.arnavgames.tycoon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    // Shape of a real GitHub "latest release" response (trimmed).
    private val release = """
        {"tag_name":"v1.1.0","name":"Arnav Business Tycoon 1.1.0","html_url":"https://github.com/o/r/releases/tag/v1.1.0",
         "assets":[
           {"name":"notes.txt","size":10,"digest":null,"browser_download_url":"https://github.com/o/r/releases/download/v1.1.0/notes.txt"},
           {"name":"ArnavBusinessTycoon-1.1.0.apk","size":10867615,
            "digest":"sha256:5180EAED273DA2B711EA5773351670ECAE7A5BDF58BFF316D57E8CABAB5F142E",
            "browser_download_url":"https://github.com/o/r/releases/download/v1.1.0/ArnavBusinessTycoon-1.1.0.apk"}]}
    """.trimIndent()

    @Test fun parsesTheApkAssetSizeAndDigest() {
        val i = UpdateChecker.parse(release)!!
        assertEquals("v1.1.0", i.version)
        assertEquals("https://github.com/o/r/releases/download/v1.1.0/ArnavBusinessTycoon-1.1.0.apk", i.apkUrl)
        assertEquals(10867615L, i.apkSize)
        assertEquals("5180eaed273da2b711ea5773351670ecae7a5bdf58bff316d57e8cabab5f142e", i.sha256)
    }

    @Test fun aReleaseWithoutAnApkFallsBackToThePage() {
        val i = UpdateChecker.parse("""{"tag_name":"v2.0.0","html_url":"https://x/y","assets":[]}""")!!
        assertNull(i.apkUrl)
        assertNull(i.sha256)
        assertEquals("https://x/y", i.url)
        assertNull(UpdateChecker.parse("""{"message":"Not Found"}"""))
    }

    @Test fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun releasePageFallbackFindsTheTagAndTheApk() {
        assertEquals("v1.3.0", UpdateChecker.tagFromLatestRedirect("https://github.com/Arnav-Dugad/Arnav-Business-Tycoon/releases/tag/v1.3.0"))
        assertEquals(null, UpdateChecker.tagFromLatestRedirect("https://github.com/Arnav-Dugad/Arnav-Business-Tycoon/releases"))
        assertEquals(null, UpdateChecker.tagFromLatestRedirect(null))
        val html = """<li><a href="/Arnav-Dugad/Arnav-Business-Tycoon/archive/refs/tags/v1.3.0.zip">Source</a></li>
            <li><a href="/Arnav-Dugad/Arnav-Business-Tycoon/releases/download/v1.3.0/ArnavBusinessTycoon-1.3.0.apk" rel="nofollow">APK</a></li>"""
        assertEquals("https://github.com/Arnav-Dugad/Arnav-Business-Tycoon/releases/download/v1.3.0/ArnavBusinessTycoon-1.3.0.apk", UpdateChecker.apkFromAssetsHtml(html))
        assertEquals(null, UpdateChecker.apkFromAssetsHtml("<p>no assets</p>"))
    }
}
