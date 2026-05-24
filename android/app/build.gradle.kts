plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    // Firebase plugins activated 2026-05-24 (Story 1.4 Phase 1). The
    // `google-services` plugin processes `app/google-services.json` at build
    // time (file is gitignored; Bania places it after Phase 0 manual setup).
    // `firebase-crashlytics` plugin wires the Crashlytics Gradle hook so
    // SafeLog's release-build Crashlytics route actually reaches the dashboard.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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

// Story 1.5 — detekt + custom ForbiddenImport rule banning android.util.Log.* and
// timber.log.Timber.* outside `logging/SafeLog.kt`. The actual rule config lives in
// `android/detekt-config.yml`; this block just points the plugin at it.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt-config.yml"))
    baseline = file("$rootDir/detekt-baseline.xml").takeIf { it.exists() }
    parallel = true
    autoCorrect = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        sarif.required.set(false)
        txt.required.set(false)
        xml.required.set(false)
        md.required.set(false)
    }
}

// Story 3.2 — ParticleProcessor fixture tests load golden files from
// `/shared/particle-rules-fixtures/` (cross-platform fixture set, repo root).
// Pass the repo-root path as a system property so the test class can resolve
// it independently of the Gradle test runner's working directory.
tasks.withType<Test>().configureEach {
    systemProperty("repo.root", rootProject.projectDir.parentFile.absolutePath)
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

    // ULID — Crockford base32 26-char time-sortable IDs (Story 1.5 + architecture §4)
    // Wrapped behind ids/UlidGenerator.kt; callers never touch the library directly.
    implementation(libs.ulid.kotlin)

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
