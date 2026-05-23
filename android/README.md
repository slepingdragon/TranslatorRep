# TranslatorRep — Android

Story 1.1 scaffold. Open this directory (`/android/`) in Android Studio Hedgehog (or newer) to import the project.

## First-Time Setup

1. **Install Android Studio** if not already (Hedgehog 2023.1.1 or newer; recommended Iguana / Jellyfish for AGP 8.7.x).
2. **Open this directory** in Android Studio: `File → Open... → /android/`.
3. **Wait for first sync.** Android Studio will:
    - Download Gradle 8.10.2 via the wrapper config (already pinned in `gradle/wrapper/gradle-wrapper.properties`).
    - Generate `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` on first sync.
    - Resolve all dependencies from Maven Central + Google's Maven repo.
    - This may take 5–10 minutes first time. Subsequent syncs are fast.
4. **Build → Make Project** (Ctrl+F9).
    - First build: ~3–5 min (downloads Kotlin compiler, KSP, Compose Compiler).
    - Subsequent incremental: <30s.
5. **Connect your Samsung Galaxy** via USB with USB debugging enabled.
6. **Run → Run 'app'** (Shift+F10). The hello-world Compose screen with the `MonochromeGlassPanel` glass-card should appear.

## What's Wired Now (Story 1.1)

- ✅ Android Empty Activity Compose project at this directory.
- ✅ Kotlin 2.1.0 + Compose BOM 2024.12.01 + AGP 8.7.3.
- ✅ minSDK 33 / target SDK 35 / Java 17 toolchain.
- ✅ Gradle Kotlin DSL + `gradle/libs.versions.toml` version catalog.
- ✅ Dependencies declared (LiveKit Android SDK 2.25.3, Firebase BOM 33.7.0 (Auth + Firestore + App Check + Crashlytics), `androidx.security:security-crypto`, Room + SQLCipher, Kotlin Coroutines + Flow). These are scaffold-only — actual usage lands in later stories.
- ✅ `AndroidManifest.xml` with the 9 permissions Story 1.1 requires (`RECORD_AUDIO`, `CAMERA`, `BLUETOOTH`+`BLUETOOTH_CONNECT`, `MANAGE_OWN_CALLS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `POST_NOTIFICATIONS`).
- ✅ `Theme.kt` overrides Material 3 `darkColorScheme` with Theme A monochrome-glass tokens (UX-DR2).
- ✅ `setDefaultNightMode(MODE_NIGHT_YES)` at `TranslatorRepApplication.onCreate()`.
- ✅ Material You dynamic color disabled (never calls `dynamicDarkColorScheme`).
- ✅ `MonochromeGlassPanel.kt` in `ui/components/` rendering backdrop blur via `RenderEffect.createBlurEffect` at Thick (24px) / Regular (16px) / Thin (8px) intensities (UX-DR5).
- ✅ Hello-world screen launches showing the glass panel.

## What's NOT Wired Yet

- ❌ `google-services.json` (Story 1.4) — Firebase plugin will fail at sync until this file exists at `/android/app/google-services.json`. **The plugin is referenced in `build.gradle.kts`; if you sync before adding `google-services.json`, you'll see a build error.** Two options:
    - **(a)** Comment out `alias(libs.plugins.google.services)` + `alias(libs.plugins.firebase.crashlytics)` in `app/build.gradle.kts` temporarily for the first sync (uncomment after Story 1.4).
    - **(b)** Drop in a placeholder `google-services.json` for sync; replace with the real one in Story 1.4.
- ❌ Gradle wrapper jar files — Android Studio generates these on first sync. If running Gradle outside Android Studio, run `gradle wrapper` once from this directory using a system-installed Gradle 8.10.2+.
- ❌ App icon — currently using `@android:drawable/sym_def_app_icon` (the placeholder Android system icon). Custom icon is Phase-5 polish.
- ❌ SafeLog + lint rules (Story 1.5) — TODO when wiring conversation-content protection.
- ❌ ULID library wrapper (Story 1.5) — TBD library pinning per `/shared/canonical-names.md`.
- ❌ CI workflow (`.github/workflows/android-ci.yml`) — Story 1.6.
- ❌ Self-managed PhoneAccount + ConnectionService — Story 2.5; commented-out in `AndroidManifest.xml` as placeholder.
- ❌ All actual features — `Call`, `Caption`, `Pair`, etc. — land in Epics 1.8+ → 8.

## Project Structure

```
android/
├── build.gradle.kts              ← top-level
├── settings.gradle.kts           ← module includes + repositories
├── gradle.properties             ← JVM args, parallel builds
├── gradle/
│   ├── libs.versions.toml        ← version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts          ← app module
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/xaeryx/translatorrep/
│       │   │   ├── TranslatorRepApplication.kt   ← MODE_NIGHT_YES, Firebase init placeholder
│       │   │   ├── MainActivity.kt               ← hello-world Compose host
│       │   │   ├── ui/theme/
│       │   │   │   ├── Color.kt                  ← Theme A monochrome-glass tokens (UX-DR2)
│       │   │   │   ├── Theme.kt                  ← darkColorScheme override; no dynamic color
│       │   │   │   └── Type.kt                   ← Typography tokens (Caption-primary 20sp, etc.)
│       │   │   └── ui/components/
│       │   │       └── MonochromeGlassPanel.kt   ← UX-DR5 primitive
│       │   └── res/
│       │       ├── values/strings.xml
│       │       ├── values/themes.xml             ← XML theme parent (status bar, etc.)
│       │       └── xml/{data_extraction_rules,backup_rules}.xml
└── .gitignore
```

## Notes on Version Pinning

See `gradle/libs.versions.toml` for the canonical version catalog with bump-rationale comments. Compose BOM ↔ AGP coupling is the trap most likely to bite — bump them together.

LiveKit Android SDK is pinned to **2.25.3** per `architecture.md` addendum because the Insertable Streams `e2eeOptions` API surface was verified at this version. Re-verify against `architecture.md` if bumping past 2.x.

## Once This Builds

Next stories in Epic 1, in suggested do-order:

1. Story 1.5 (SafeLog + lint + ULID) — generate `logging/SafeLog.kt` + detekt rule + `ids/UlidGenerator.kt`.
2. Story 1.4 (Firebase) — drop in `google-services.json`, wire `Application.onCreate()` Firebase init, test anonymous sign-in.
3. Story 1.6 (CI/CD) — write `.github/workflows/android-ci.yml`.
4. Story 1.8 (anonymous sign-in user flow) — first user-facing screen.
5. Then through Story 1.13, then Epic 2 (Audio Calling).

Cross-platform parity work happens in lockstep with iOS — see [/ios/](../ios/) and [docs/runbooks/ios-setup-on-mac.md](../docs/runbooks/ios-setup-on-mac.md).
