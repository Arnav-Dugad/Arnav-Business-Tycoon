// Generates the app's Baseline Profile (startup and first-session journeys) on a connected API 33+ device or emulator:
//   ./gradlew :app:generateReleaseBaselineProfile
// The result is written to app/src/release/generated/baselineProfiles/ and packaged with release builds.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.arnavgames.tycoon.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
