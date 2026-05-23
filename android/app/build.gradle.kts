plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // Firebase plugins disabled until Story 1.4 lands `google-services.json`
    // at `app/google-services.json`. UN-COMMENT both lines at Story 1.4:
    //   alias(libs.plugins.google.services)
    //   alias(libs.plugins.firebase.crashlytics)
    // The Firebase BOM + per-SDK dependencies in `dependencies { ... }` below
    // still resolve fine without these plugins; they're just inert (no
    // FirebaseApp.initializeApp call wired) until Story 1.4.
}

android {
    namespace = "com.xaeryx.translatorrep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xaeryx.translatorrep"
        minSdk = 33                       // Story 1.1 AC — on-device SpeechRecognizer requires API 33+
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Pin a stable test resource directory for the cross-platform fixture
        // harness (Story 3.2 / Story 1.7). Fixtures live in /shared/ at the
        // repo root; Gradle copies them into androidTest resources at build.
    }

    buildTypes {
        debug {
            // BuildConfig flag for Story 3.11 three-layer translation capture
            // pipeline (debug-only, default OFF). Real flag wiring lives in
            // Story 3.11; declared here so it's plumbed from day one.
            buildConfigField("Boolean", "TRANSLATION_TRACE_ENABLED", "false")
        }
        release {
            isMinifyEnabled = false       // Will enable when ProGuard rules stabilize (post Epic 4)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("Boolean", "TRANSLATION_TRACE_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Material You dynamic color is OFF per UX-DR2; we don't even need this
    // declared at build level — it's handled in Theme.kt by never calling
    // dynamicDarkColorScheme/dynamicLightColorScheme.
}

dependencies {
    // Compose BOM — single source of truth for Compose artifact versions
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // LiveKit Android SDK — WebRTC media + Data Channel + Insertable Streams
    // (Architecture ADR-A1 + ADR-A2). Pinned per architecture.md addendum.
    implementation(libs.livekit.android)

    // Firebase — BOM-managed; Story 1.4 wires actual usage
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)
    debugImplementation(libs.firebase.appcheck.debug)  // dev-only DebugAppCheckProvider

    // Local persistence — Room + SQLCipher for FR-21 transcript history (Epic 8)
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.security.crypto)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
