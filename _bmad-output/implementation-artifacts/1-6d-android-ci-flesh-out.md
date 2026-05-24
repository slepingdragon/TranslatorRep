# Story 1.6d: Android CI Flesh-Out — Signing Config + assembleRelease + Compose UI Test Scaffold

Status: review

<!-- Created 2026-05-24 by feature/1-6d-signing-roborazzi-uitest branch.
     Spun out from Story 1.6 CR review as a deferred follow-up.
     SCOPE REVISION 2026-05-24: Roborazzi screenshot testing deferred to a
     new sub-story 1-6e-roborazzi-screenshot-tests because (a) Robolectric
     NATIVE graphics mode has Windows-specific Skia native-lib loading issues
     that risk wasting time, (b) screenshot testing is orthogonal to the
     immediate goal (unblock Story 1.4c Play Store Internal Testing — which
     only needs working signing + assembleRelease), (c) splitting keeps this
     PR reviewable. -->

## Story

As a solo developer preparing for Play Store Internal Testing distribution,
I want a working signing config + locally-buildable `assembleRelease` task + a Compose UI instrumented-test scaffold,
so that Story 1.4c (Play Store Internal Testing CI wiring) has the release-build infrastructure it needs to upload a properly-signed AAB to Play Console.

## Acceptance Criteria

**Given** Story 1.6 (Android CI) is `done` (detekt + unit tests + assembleDebug + JUnit annotations + 7-day APK artifact live) and Story 1.4 (Firebase) is `review`,
**When** this story lands,
**Then:**

1. **AC-1 (Signing config supports release):** `android/app/build.gradle.kts` has a `signingConfigs.release` block that reads `storeFile`, `storePassword`, `keyAlias`, `keyPassword` from `android/app/keystore.properties` (gitignored) **if present**, otherwise falls back to the existing debug signing config with a Gradle warning logged. Local `./gradlew :app:assembleRelease` succeeds regardless of whether `keystore.properties` exists — proves the fallback works.
2. **AC-2 (assembleRelease task functional):** `./gradlew :app:assembleRelease` produces `android/app/build/outputs/apk/release/app-release.apk`. APK is signed (debug key if no `keystore.properties`; real upload key if Bania has generated one). Local-only verification; CI does NOT run this task in this PR (deferred to Story 1.4c which adds the GH Actions keystore secret).
3. **AC-3 (Compose UI instrumented test scaffold):** `android/app/src/androidTest/java/com/xaeryx/translatorrep/MainActivityComposeTest.kt` exists as a minimal example using `createAndroidComposeRule<MainActivity>()`. Compiles via `./gradlew :app:compileDebugAndroidTestKotlin` — proves the test dependencies + AndroidManifest test entries are wired. NOT run in CI — instrumented tests need an emulator runner (Firebase Test Lab or Gradle Managed Devices), deferred until ROI is clear.
4. **AC-4 (Keystore generation documented):** [`docs/runbooks/release-keystore-setup.md`](../../docs/runbooks/release-keystore-setup.md) exists with a complete `keytool` walkthrough for Bania to generate the upload key when starting Story 1.4c. Includes: command, password best practices (1Password / Bitwarden), backup-the-keystore warning, `keystore.properties` template, and verification steps.
5. **AC-5 (Existing CI passes unchanged):** `.github/workflows/android-ci.yml` is unchanged in this PR (no new CI steps). All existing checks (detekt + unit tests + assembleDebug + JUnit + APK artifact) continue to pass.

**Done criteria:** Story 1.6d flips to `review` when all 5 ACs ✅ → CR pass → `done`. Cascades-unblocks Story 1.4c (which adds the real keystore + CI release-build + Play Console upload).

**Deferred (NEW sub-story 1-6e):** Roborazzi Compose screenshot tests in CI. Reason: Windows-specific Robolectric NATIVE graphics setup risk + irrelevant to the QR-install goal that's driving this work.

## Tasks / Subtasks

### Phase 1 — Signing config

- [ ] **1.1** Add `signingConfigs.release` block in `android/app/build.gradle.kts`:
  ```kotlin
  signingConfigs {
      create("release") {
          val propsFile = rootProject.file("app/keystore.properties")
          if (propsFile.exists()) {
              val props = Properties().apply { propsFile.inputStream().use(::load) }
              storeFile = rootProject.file(props.getProperty("storeFile"))
              storePassword = props.getProperty("storePassword")
              keyAlias = props.getProperty("keyAlias")
              keyPassword = props.getProperty("keyPassword")
          } else {
              logger.warn(
                  "keystore.properties not found — release builds will use debug keystore. " +
                  "Run docs/runbooks/release-keystore-setup.md when ready for Play Store."
              )
              // Fall back to debug signing config (so `assembleRelease` still works locally).
              initWith(signingConfigs.getByName("debug"))
          }
      }
  }
  ```
- [ ] **1.2** Wire `signingConfig = signingConfigs.getByName("release")` into `buildTypes.release`.
- [ ] **1.3** Verify `.gitignore` already excludes `keystore.properties` (it does — per existing `*.jks`, `*.keystore`, `keystore.properties` rules at `android/.gitignore`).
- [ ] **1.4** Local test: `./gradlew :app:assembleRelease --no-daemon` succeeds and produces `app/build/outputs/apk/release/app-release.apk`. Verify with `jarsigner -verify app-release.apk` that the APK is signed (currently with debug key).

### Phase 2 — Compose UI instrumented test scaffold

- [ ] **2.1** Create `android/app/src/androidTest/java/com/xaeryx/translatorrep/MainActivityComposeTest.kt`:
  ```kotlin
  @RunWith(AndroidJUnit4::class)
  class MainActivityComposeTest {
      @get:Rule
      val composeRule = createAndroidComposeRule<MainActivity>()

      @Test fun helloWorld_displays_TranslatorRep_title() {
          composeRule.onNodeWithText("TranslatorRep").assertIsDisplayed()
      }
  }
  ```
- [ ] **2.2** Verify the test class compiles: `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`. Do NOT run the test (needs an emulator; CI activation deferred).

### Phase 3 — Keystore-generation runbook

- [ ] **3.1** Create `docs/runbooks/release-keystore-setup.md` with:
  - The `keytool -genkey -v -keystore translatorrep-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias translatorrep` command.
  - Password best practices: use 1Password or Bitwarden; key alias passwords should match store password for simplicity.
  - **CRITICAL warning:** losing this keystore = you can never update the app on Play Store again. Back it up multiple places (1Password vault + offline encrypted USB + cloud backup of the encrypted file).
  - `keystore.properties` template + where to place it.
  - Verification step: `./gradlew :app:assembleRelease` should now sign with the real key, not the debug key.
  - Where this keystore is consumed by CI later (Story 1.4c GH secret pattern).

### Phase 4 — Sprint-status + project-context updates

- [ ] **4.1** Update `sprint-status.yaml`: 1-6d `backlog` → `in-progress` → `review`. Add new 1-6e row for the deferred Roborazzi work.
- [ ] **4.2** Update `docs/project-context.md` §10 — 1-6d row reflects this PR's state; add 1-6e row.

## Dev Notes

### Why split Roborazzi to a follow-up (1-6e)

Original Story 1.6d scope (per Story 1.6 CR doc) included Compose UI tests + Roborazzi + assembleRelease + signing. Roborazzi is being deferred because:

1. **Windows-specific risk.** Roborazzi's Compose renderer uses Robolectric's NATIVE graphics mode, which loads Skia via JNI. On Windows this requires a specific Skia native library + sometimes Visual C++ runtime quirks. Time spent debugging this isn't on the QR-install critical path.
2. **CI signal vs. cost.** Screenshot tests catch UI regressions but generate noisy diffs on intentional changes. For solo-dev pre-product-validation, the value isn't there yet.
3. **PR reviewability.** Combining 4 substantial pieces (signing, assembleRelease, UI test scaffold, screenshot tests) makes the PR harder to review. Splitting screenshot tests out keeps each PR focused.

Sub-story `1-6e-roborazzi-screenshot-tests` (to be created) picks this up when there's clear ROI — likely after Epic 8 (UI polish) when UI changes become more frequent + intentional.

### Why signing config falls back to debug keystore

Two competing goals:
1. **Local-build ergonomics:** Bania shouldn't need to generate a keystore on day 1 just to run `assembleRelease`. Friction.
2. **Real release signing:** Play Store requires a stable upload key.

Fall-back pattern solves both: local builds work via debug fallback (compiles, signed, just not Play-Store-publishable); real release builds for Play Console require `keystore.properties` present (Bania generates it when starting Story 1.4c).

CI doesn't run `assembleRelease` in this PR because debug-fallback isn't useful for CI (the resulting APK isn't publishable). Story 1.4c adds CI release signing with a GH Actions secret containing the real keystore base64'd — same pattern as the `GOOGLE_SERVICES_JSON` secret from Story 1.4 Phase 1.

### Why Compose UI instrumented test scaffold but no CI

Compose instrumented tests need a real device or emulator. CI options + trade-offs:
- **Firebase Test Lab** — Google's cloud emulator. Free quota generous; paid after that. Adds GCP + service account setup overhead.
- **Gradle Managed Devices** — emulator inside the GH Actions runner. ~3-5 min per test (cold AVD startup). For solo dev pre-feature-complete, not worth the CI time.
- **Defer to local-only** — Bania runs instrumented tests via Android Studio's Run UI Test before merging anything UI-heavy.

Per solo-dev posture: defer CI integration. Scaffold the dir + a sample test so the pattern is in place; future activation triggered by a clear ROI signal (e.g., a flaky Compose UI bug that keeps slipping through unit tests).

### Library + SDK references

- [Android signing configs in Gradle Kotlin DSL](https://developer.android.com/build/building-cmdline#sign_cmdline)
- [keytool reference (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
- [Compose UI testing — createAndroidComposeRule](https://developer.android.com/develop/ui/compose/testing#createAndroidComposeRule)
- [Play App Signing overview](https://support.google.com/googleplay/android-developer/answer/9842756) — explains why Play Store wants an "upload key" separate from the "app signing key" Google manages

### Source-tree placement

```
android/
├── app/
│   ├── build.gradle.kts                                    # MODIFIED: signing config
│   ├── keystore.properties                                 # NEW (gitignored; Bania creates per runbook later)
│   └── src/
│       └── androidTest/                                    # NEW dir
│           └── java/com/xaeryx/translatorrep/
│               └── MainActivityComposeTest.kt              # NEW (compiles; CI not wired)

docs/runbooks/
└── release-keystore-setup.md                               # NEW (Bania's keystore-gen walkthrough)
```

### Testing standards

- **Local `assembleRelease`** must succeed without `keystore.properties` (debug-fallback path). Verified via local `./gradlew` invocation.
- **Compose instrumented test scaffold** compiles only; running deferred until CI integration story.
- **No new unit tests** introduced — Phase 4 of 1.6d was Roborazzi (deferred to 1-6e).

### References

- [Story 1.6](./1-6-cicd-per-stack.md) — parent CI story
- [Story 1.4c](../../_bmad-output/implementation-artifacts/...) — to be created; consumes signing infrastructure
- [Story 1-6e (deferred)](../../_bmad-output/implementation-artifacts/...) — Roborazzi screenshot tests (to be created)
- [architecture.md §"CI/CD Per Stack"](../planning-artifacts/architecture.md)

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-PR-15-merge)

### Debug Log References

- Local validate: `./gradlew :app:detekt :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleRelease --no-daemon` → BUILD SUCCESSFUL in 57s. All 5 tasks green. 0 detekt smells. Pre-existing Theme.kt deprecation warnings (statusBarColor / navigationBarColor — unrelated to this story) appeared as expected.
- APK signature verification: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` → `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug` + SHA-1 `6b99a73d932937995153d2fb2e09376ddb6a9186` (matches Bania's debug keystore from earlier signingReport). Confirms fallback worked correctly.
- Initial verification attempted with `keytool -printcert -jarfile` which returned "Not a signed jar file" — modern APKs use V2/V3 signing which keytool can't read. Switched to `apksigner` (per APK Signature Scheme v2 docs). Runbook updated with the correct command.

### Completion Notes List

- Signing config wired with explicit fallback: reads `app/keystore.properties` if present, else `initWith(signingConfigs.getByName("debug"))` + Gradle warning log. Local `assembleRelease` works on day 1 without forcing keystore generation; APK signed (debug fallback), just not Play-Store-publishable.
- Compose UI test scaffold at `androidTest/java/.../MainActivityComposeTest.kt` — single test asserting "TranslatorRep" text displays. Compiles via `compileDebugAndroidTestKotlin`; not CI-run (deferred until clear ROI for emulator-runner setup cost).
- `docs/runbooks/release-keystore-setup.md` documents the full keytool flow + safekeeping warnings (3-place backup) + GH Actions secret preview for Story 1.4c. Bania only needs to follow this when starting 1.4c.
- New dependency: `java.util.Properties` import added to `app/build.gradle.kts` — first use of Properties in this file.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-6d-android-ci-flesh-out.md` — this file
- `android/app/src/androidTest/java/com/xaeryx/translatorrep/MainActivityComposeTest.kt` — Compose UI test scaffold (compile-only)
- `docs/runbooks/release-keystore-setup.md` — keystore generation walkthrough

**Modified:**
- `android/app/build.gradle.kts` — added `import java.util.Properties` + `signingConfigs.release` block + `signingConfig = signingConfigs.getByName("release")` in `buildTypes.release`
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-6d backlog → review; new 1-6e backlog row; last_updated bump
- `docs/project-context.md` §10 — 1-6d row updated to landed/review; new 1-6e row for deferred Roborazzi

### Change Log

- 2026-05-24 — Story 1.6d file created (status `in-progress`). SCOPE REVISION at story-creation time: Roborazzi screenshot testing deferred to a new sub-story 1-6e because Windows-specific Robolectric NATIVE graphics issues + screenshot tests not on the immediate QR-install critical path. 1.6d focuses on signing config + assembleRelease + Compose UI test scaffold to unblock Story 1.4c.
- 2026-05-24 (later) — **Implementation landed.** Signing config + assembleRelease + Compose UI test scaffold + keystore runbook all in place. Local validate green. Status → `review`. Cascades-unblocks Story 1.4c.
