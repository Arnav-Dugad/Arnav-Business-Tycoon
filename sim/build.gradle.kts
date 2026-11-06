// Headless balance simulator (bots + reports). Uses the same :core engine and content as the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.arnavgames.tycoon.sim.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    systemProperty("abt.contentDir", rootProject.layout.projectDirectory.dir("content").asFile.absolutePath)
    maxHeapSize = "6g"
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("abt.contentDir", rootProject.layout.projectDirectory.dir("content").asFile.absolutePath)
    // Content is read at runtime, so declare it as an input or Gradle may skip the tests after a balance change.
    inputs.dir(rootProject.layout.projectDirectory.dir("content"))
}
