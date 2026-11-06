package com.arnavgames.tycoon.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * JUnit silently ignores test methods that do not return void. `fun x() = runBlocking { checkAll(...) }`
 * returns a value, so such a property test would never run. This guard keeps that from happening again.
 */
class TestHygieneTest {
    @Test
    fun noTestFunctionReturnsAValue() {
        val bad = Regex("""@Test\s+fun\s+\w+\(\)\s*=""")
        val offenders = File("src/test/kotlin").walk()
            .filter { it.isFile && it.extension == "kt" }
            .filter { bad.containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders, "declare test functions as `fun x(): Unit = …` or with a block body")
    }
}
