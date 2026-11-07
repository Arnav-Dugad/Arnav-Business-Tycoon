plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

// Firebase config is never committed. Without it the app builds as the "offline edition".
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Semantic version. versionCode = major*10000 + minor*100 + patch.
// -Pabt.versionOverride=1.1.1 builds another version (used only by the auto-update end-to-end test).
val versionParts = (providers.gradleProperty("abt.versionOverride").orNull ?: "1.2.0").split('.').map { it.toInt() }
val versionMajor = versionParts[0]
val versionMinor = versionParts[1]
val versionPatch = versionParts[2]
// Where the in-app updater looks for the latest release. -Pabt.updateApi=... points a debug build at a local test server.
val updateApi = providers.gradleProperty("abt.updateApi").orNull
    ?: "https://api.github.com/repos/Arnav-Dugad/Arnav-Business-Tycoon/releases/latest"

android {
    namespace = "com.arnavgames.tycoon"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arnavgames.tycoon"
        minSdk = 28
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Every instrumented test starts from a fresh install state (no leftover save).
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
        buildConfigField("String", "GITHUB_REPO", "\"Arnav-Dugad/Arnav-Business-Tycoon\"")
        buildConfigField("String", "UPDATE_API", "\"$updateApi\"")
    }

    // Release signing reads credentials from ~/.gradle/gradle.properties (outside the repo).
    val keystorePath = providers.gradleProperty("ABT_KEYSTORE").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("ABT_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("ABT_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("ABT_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        // Release-optimized (R8, not debuggable) but signed with the debug key so it installs on test devices.
        // Used for realistic performance measurement and baseline-profile generation.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".bench"
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // Validated game content is shared with :core tests and :sim.
            assets.directories.add(rootProject.layout.projectDirectory.dir("content").asFile.path)
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "DebugProbesKt.bin")
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 36+ runtime needs these on modern JDKs.
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            // Robolectric renders the whole app for screenshot tests; keep it within the laptop budget.
            it.maxHeapSize = "1536m"
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":designsystem"))

    implementation(libs.coil.compose)
    // Firebase (Spark plan only): Auth + Firestore. Without google-services.json the app runs offline (release) or
    // against the local Firebase emulator with a "demo-" project (debug).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    // Google sign-in through Credential Manager (shown only when google-services.json has a web client id).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    // JVM screenshot tests of the real app (Robolectric + Roborazzi): docs/PLAN_2.0.md I3. No emulator needed.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose UI test pulls an old Espresso that calls APIs removed in Android 17; pin a current one.
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.core)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
