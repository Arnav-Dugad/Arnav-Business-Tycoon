plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Guard: :core must stay pure Kotlin/JVM (no Android dependencies). See docs/ARCHITECTURE.md §2.
tasks.register("verifyCorePurity") {
    group = "verification"
    description = "Fails if :core declares any Android or AndroidX dependency."
    val buildFile = layout.projectDirectory.file("core/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val text = buildFile.asFile.readText()
        val banned = listOf("androidx.", "com.android", "android-", "libs.androidx", "libs.compose", "libs.hilt", "libs.firebase")
        val hits = banned.filter { text.contains(it) }
        check(hits.isEmpty()) { ":core must not depend on Android. Found: $hits" }
    }
}
