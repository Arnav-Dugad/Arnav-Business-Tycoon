// Pure Kotlin/JVM module: all game rules. NO Android dependencies (checked by :verifyCorePurity).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Content JSON lives in /content at the repo root; tests read it from there.
tasks.test {
    useJUnitPlatform()
    systemProperty("abt.contentDir", rootProject.layout.projectDirectory.dir("content").asFile.absolutePath)
    // Only set when deliberately creating the golden save fixture for a new schema version.
    systemProperty("abt.writeFixtures", providers.gradleProperty("abt.writeFixtures").orElse("false").get())
    inputs.dir(rootProject.layout.projectDirectory.dir("content"))
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
