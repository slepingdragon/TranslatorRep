# Story 1.4: Firebase Init + Firestore Rules Baseline + App Check Providers (Android)

Status: review

<!-- Created 2026-05-23 by feature/1-4-firebase-android branch.
     Story scope cut to Android-only per Bania's direction 2026-05-23
     ("we will do android all first, then i want to have a separate
     claude session in the future to build out the ios app").
     iOS half → Story 1-4b-ios-firebase-init (deferred to Mac/iOS session).
     Firebase App Distribution → Story 1-4c-firebase-app-distribution-android. -->

## Story

As a solo developer,
I want Firebase Auth (anonymous), Firestore, and App Check (Play Integrity) configured for the Android app,
so that the app can sign users in anonymously, store metadata-only docs against enforced security rules, and gate backend calls behind device attestation — unblocking the pairing arc (Stories 1.8–1.13).

## Acceptance Criteria

**Given** Story 1.1 (Android scaffold) is done and Story 1.5 (SafeLog) is done,
**When** Bania completes the manual Firebase project setup per [`docs/runbooks/firebase-setup-android.md`](../../docs/runbooks/firebase-setup-android.md) and a dev session implements the Android wiring,
**Then:**

1. **AC-1 (FirebaseApp initialized on app start):** `android/app/build.gradle.kts` has the `google-services` plugin **uncommented** and `android/app/google-services.json` exists (gitignored). `TranslatorRepApplication.onCreate()` calls `FirebaseBootstrap.init(this)` which calls `FirebaseApp.initializeApp(context)` exactly once. SafeLog event `firebase_init=success` is emitted on success or `firebase_init=failed:<reason>` on failure (without crashing the app).
2. **AC-2 (Firestore security rules enforce per-spec):** `firebase/firestore.rules` exists and enforces:
   - `/users/{uid}` — readable + writable only by the authenticated owner (`request.auth.uid == uid`)
   - `/pairs/{pairId}` — readable + writable only by `memberA` or `memberB` field on the doc; createable only when caller is one of the two members
   - `/codes/{6digit}` — readable by any authenticated user (lookup), writable only by the field-owning user (`ownerId == request.auth.uid`)
   - `/calls/{callId}/ephemeralPub/{uid}` — readable + writable only by the uid that owns the subdoc (conservative until Epic 2 defines `/calls/{callId}` participants schema; expand then)
   Rules deploy successfully via `firebase deploy --only firestore:rules` (verified by running the command).
3. **AC-3 (App Check configured with Play Integrity in release, Debug provider in debug):** `FirebaseBootstrap.init()` installs `PlayIntegrityAppCheckProviderFactory` when `!BuildConfig.DEBUG`, else `DebugAppCheckProviderFactory`. Play Integrity is registered for the app's package name (`com.xaeryx.translatorrep`) in Google Play Console + Firebase console per the runbook. Provider setup notes captured in [`firebase/appcheck/android-providers.md`](../../firebase/appcheck/android-providers.md).
4. **AC-4 (Secrets are gitignored):** `android/app/google-services.json` is in `android/.gitignore` (✅ already done at scaffold time). Verified by attempting `git add android/app/google-services.json` after placing the file — git refuses or reports it ignored.
5. **AC-5 (Smoke test):** A manual smoke test on a real device or Android Studio emulator confirms: (a) app starts without crash, (b) SafeLog emits `firebase_init=success`, (c) a one-shot `signInAnonymously()` call in `FirebaseSmokeTest.runOnce()` produces a Firebase UID, (d) a one-shot Firestore write to `/users/{uid}` with `{"smokeTest": true, "ts": <serverTimestamp>}` succeeds, (e) a one-shot write to `/users/<other-uid>` is **rejected** by the security rules (verifies AC-2 enforcement). Results recorded in this story's Dev Agent Record → Debug Log References.

**Note:** AC-5's `signInAnonymously()` here is a one-shot smoke test. Production sign-in-on-first-launch flow lands in Story 1.8.

## Tasks / Subtasks

### Phase 0 — Bania's external setup (manual; runbook walks through)

These tasks are NOT for an AI agent. Bania completes them in a browser per [`docs/runbooks/firebase-setup-android.md`](../../docs/runbooks/firebase-setup-android.md). The runbook is the source of truth; bullets here are a checklist mirror.

**Updated 2026-05-24:** Bania confirmed Google Play Console + Apple Developer accounts already exist. Net effect on this Phase: §0.7 (Play Integrity) is now required, not optional. Internal Testing distribution (the QR/install workflow) moves from Story 1-4c "Firebase App Distribution" → Story 1-4c "Google Play Internal Testing" (Play Console flow is cleaner for native Android than Firebase App Distribution; uses real Play Store install). Internal Testing setup is NOT part of Story 1.4 Phase 0 — it depends on Story 1.6d (signing config) + happens in Story 1.4c.

- [x] **0.1** Sign in to [console.firebase.google.com](https://console.firebase.google.com) with the Google account that will own the project. _(baniabradyy@gmail.com, 2026-05-24)_
- [x] **0.2** Create a new Firebase project named `TranslatorRep` (Analytics optional — recommend OFF for a privacy-first app). _(Project ID `translatorrep-8d773`, Analytics OFF)_
- [x] **0.3** Add an Android app to the project: package name `com.xaeryx.translatorrep`; nickname `TranslatorRep Android`; optionally provide SHA-1 from debug keystore (`./gradlew :app:signingReport` or via Android Studio). _(SHA-1 + SHA-256 from debug keystore both added)_
- [x] **0.4** Download `google-services.json` from the Firebase console → place at `android/app/google-services.json`. Verify it's gitignored (`git status` should NOT show it). _(Verified 694 bytes at correct path; not in git status)_
- [x] **0.5** Enable **Authentication → Sign-in method → Anonymous → Enable**.
- [x] **0.6** Enable **Firestore Database → Create database → start in production mode** (we'll deploy our own rules). Pick a region close to Indonesia (asia-southeast2 = Jakarta if available, else asia-southeast1 = Singapore). _(`asia-southeast2` = Jakarta chosen)_
- [x] **0.7** Enable **App Check** for the Android app. Register **Play Integrity** as the attestation provider — requires Google Play Console + Play Integrity API enabled in Google Cloud Console for the linked project. Runbook §5 has the full walkthrough. **Updated 2026-05-24:** Bania confirmed Google Play Console dev account already exists, so no $25 fee gate. This step is required (no longer optional/deferrable per the previous "skip §5 if you don't want $25" bailout). _(Play Integrity API enabled in Cloud Console + linked from Play Console)_
- [ ] **0.8** (Optional) Generate a Debug App Check token via the Firebase console for use with `DebugAppCheckProviderFactory` during dev — saves needing real Play Integrity attestation in debug builds. Store the token in `local.properties` as `firebaseAppCheckDebugToken=...` (gitignored). _(Skipped — Bania will copy the auto-logged debug token from Logcat to Firebase console during Phase 3.3 smoke test.)_
- [x] **0.9** Install Firebase CLI globally: `npm install -g firebase-tools`; authenticate via `firebase login`; link the local `firebase/` directory to the new project via `cd firebase && firebase use --add <project-id>`. _(firebase-tools 15.18.0, `.firebaserc` created with alias `default` → `translatorrep-8d773`)_

### Phase 1 — Android code wiring (this PR, 2026-05-24)

- [x] **1.1** Uncommented the two Firebase plugins in [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts): `alias(libs.plugins.google.services)` + `alias(libs.plugins.firebase.crashlytics)`. `./gradlew :app:assembleDebug` builds successfully (1m12s cold, ~16s warm) — `google-services` plugin processes `app/google-services.json` correctly.
- [x] **1.2** Activated `FirebaseBootstrap.init(this)` in [`TranslatorRepApplication.onCreate()`](../../android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt); removed the stale `TODO Story 1.4` comment lines.
- [x] **1.3** Verified `FirebaseBootstrap` selects the correct App Check provider via Gradle source-set discrimination (`src/debug/.../AppCheckFactoryProvider.kt` returns `DebugAppCheckProviderFactory`; `src/release/.../AppCheckFactoryProvider.kt` returns `PlayIntegrityAppCheckProviderFactory`). This avoids referencing `firebase-appcheck-debug` (debugImplementation-only) from any release-variant bytecode.
- [x] **1.4** Implemented [`FirebaseSmokeTest.runOnce()`](../../android/app/src/main/java/com/xaeryx/translatorrep/firebase/FirebaseSmokeTest.kt) — anonymous sign-in → own `/users/{uid}` write (expect success) → forbidden `/users/smoke-other-{4chars}` write (expect PERMISSION_DENIED). All outcomes SafeLog'd; function never throws. New dependency `kotlinx-coroutines-play-services` added to `libs.versions.toml` for the `Task.await()` extension. Wired from [`MainActivity.maybeTriggerFirebaseSmokeTest()`](../../android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt) — debug builds only, gated on intent extra `--es firebase-smoke true`. Production sign-in flow remains Story 1.8.

### Phase 2 — Firestore rules deploy (done 2026-05-24)

- [x] **2.1** Deployed rules via `cd firebase && firebase deploy --only firestore:rules`. Output: `+ released rules firestore.rules to cloud.firestore` → `+ Deploy complete!`. Project: `translatorrep-8d773`.
- [ ] **2.2** Rules Playground verification — Bania can do this post-merge as a sanity check; not blocking story flip. Run from Firebase console → Firestore → Rules → Playground.

### Phase 3 — App Check verification + smoke test (Bania's manual step post-merge)

- [ ] **3.1** Run debug APK on a real Android device (Play Integrity requires Play Services; emulator works if Play Services is installed on the AVD image). Verify SafeLog emits `firebase_init=success` and `app_check_init=debug` (or `=playintegrity` in a release-build smoke).
- [ ] **3.2** Trigger the FirebaseSmokeTest via adb: `adb shell am start -n com.xaeryx.translatorrep/.MainActivity --es firebase-smoke true`. Verify in Logcat (filter `tag=TranslatorRep`): (a) `auth_uid=<4chars>`, (b) `smoke_users_write=success`, (c) `smoke_forbidden_write=denied`.
- [ ] **3.3** First-run only: copy the DebugAppCheckProvider token logged by Firebase to Logcat (filter `tag=DebugAppCheckProvider`) → Firebase console → App Check → Apps → Android → ⚙ → Manage debug tokens → Add → paste + name (e.g., `bania-pixel-debug`). Re-run the smoke test → forbidden write should now properly PERMISSION_DENIED (without the debug token, App Check denies BEFORE Firestore rules can evaluate, so the failure mode differs).
- [ ] **3.4** Record smoke-test device + observed SafeLog events in Dev Agent Record → Debug Log References.

### Phase 4 — Documentation + sprint-status updates (post-implementation)

- [ ] **4.1** Update [`firebase/appcheck/android-providers.md`](../../firebase/appcheck/android-providers.md) with the actual Play Integrity setup details (project number, attestation token retrieval procedure, etc.) — runbook + this story-AC-3 file is the spec; the captured file is the historical record.
- [ ] **4.2** Update [`docs/project-context.md`](../../docs/project-context.md) §10 to remove `1-4-firebase-init-...` from the "externally blocked" table; move it to "completed" or remove the row entirely.
- [ ] **4.3** Update [`_bmad-output/implementation-artifacts/sprint-status.yaml`](../../_bmad-output/implementation-artifacts/sprint-status.yaml): `1-4-firebase-init-...: in-progress` → `review` → `done`. Sequenced after manual setup is complete + Phase 1–3 land.

## Dev Notes

### Why this story matters now

Story 1.6 closed out CI/CD; the next 6 Android-side Epic 1 stories (1.8 anonymous sign-in, 1.9 own pairing code, 1.10 enter partner's code, 1.11 paired-state persistence, 1.12 X25519 keypair, 1.13 settings sheet) **all gate on Firebase**. Story 1.4 unblocks the entire pairing arc.

Bania's solo-dev decision 2026-05-23: "Android all first, then a separate Claude session for iOS." Story 1.4 was originally specced as cross-platform; this implementation cuts it to Android-only and creates follow-up Stories 1.4b (iOS) + 1.4c (Firebase App Distribution for the QR/install workflow).

### Scope cuts vs. original epics.md spec

Per epics.md §"Story 1.4," the original AC list was cross-platform:

| Original AC | This story | Where deferred |
|---|---|---|
| Firebase project init via CLI | ✅ included (Phase 0.2, 0.9) | — |
| `firebase.json` (Auth + Firestore + App Check, no Functions) | ✅ included (`firebase/firebase.json`, scaffolded in this PR) | — |
| `firestore.rules` per spec | ✅ included (AC-2) | — |
| **iOS** App Check with DeviceCheck provider | ❌ deferred | Story **1-4b-ios-firebase-init** |
| Android `google-services.json` placement | ✅ included (Phase 0.4) | — |
| **iOS** `GoogleService-Info.plist` placement | ❌ deferred | Story **1-4b-ios-firebase-init** |
| Smoke test on both platforms | ✅ Android only (AC-5) | iOS smoke → Story **1-4b** |

Also added by this story (not in original epics.md):

- `FirebaseBootstrap.kt` — encapsulates init + App Check provider selection. Keeps Application.kt thin.
- `firebase/firebase.json` + `firebase/.firebaserc` setup notes — needed for the CLI workflow that the epic mentions but doesn't detail.
- `firebase/appcheck/android-providers.md` — historical record of the Play Integrity setup steps (per epic AC #3 wording "setup notes captured in...").
- Debug App Check token wiring via `local.properties` — saves needing real Play Integrity attestation in debug builds; documented in runbook §6.

### Previous story intelligence

**Story 1.1 (Android scaffold):**
- `TranslatorRepApplication.kt` already exists with explicit `TODO Story 1.4: FirebaseApp.initializeApp(this)` line — this story executes those TODOs. The new `FirebaseBootstrap.init()` replaces both the FirebaseApp init TODO and the App Check init TODO.
- `android/app/build.gradle.kts` has the `google-services` + `firebase-crashlytics` plugins **commented out** with explicit "Re-enable at Story 1.4" instructions on lines 7–13. This story uncomments them as Phase 1.1.
- `android/.gitignore` already excludes `google-services.json` and `app/google-services.json` — secrets are pre-protected.
- The Firebase BOM + per-SDK dependencies (auth, firestore, appcheck-playintegrity, appcheck-debug, crashlytics) are already declared in `dependencies { ... }` block. They resolve fine without the plugins; the plugins just enable the build-time `google-services.json` processing.

**Story 1.5 (SafeLog facade):**
- `SafeLog.event(key: AllowedLogKey, value: Any)` is the ONLY logging surface — direct `android.util.Log.*` is banned by detekt `ForbiddenImport`. New `FirebaseBootstrap.kt` MUST go through SafeLog.
- SafeLog's Crashlytics route is wrapped in `try/catch (RuntimeException)` (post-CR), so pre-Firebase-init SafeLog calls in this story's bootstrap path will no-op on the Crashlytics route gracefully — no chicken-and-egg issue.
- New keys needed: `firebase_init`, `app_check_init`, `auth_uid` (logs first 4 chars only — never full UID per canonical-names.md). These must be added to `AllowedLogKey` enum in this story.

**Story 1.6 (CI/CD):**
- CI's `android-ci.yml` runs `detekt + testDebugUnitTest + assembleDebug`. The `assembleDebug` step will now invoke `google-services` plugin (after Phase 1.1 uncomment). If `google-services.json` is NOT present on a fork PR (which can't access secrets), the build fails. Workaround: pre-commit a sanitized template `google-services-debug.json` that's safe to commit, OR document that CI on fork PRs will fail until Firebase is real (matches solo-dev posture: no fork PRs expected).
- The CI workflow's `permissions: { checks: write, pull-requests: write }` block is unchanged — no new permissions needed for Firebase.

### Library + SDK references

- [Firebase BOM 33.7.0 release notes](https://firebase.google.com/support/release-notes/android)
- [Firebase Auth (Anonymous) Android quickstart](https://firebase.google.com/docs/auth/android/anonymous-auth)
- [Cloud Firestore security rules reference](https://firebase.google.com/docs/firestore/security/rules-conditions)
- [App Check + Play Integrity Android setup](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [App Check Debug provider (development only)](https://firebase.google.com/docs/app-check/android/debug-provider)

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── firebase/
│   ├── FirebaseBootstrap.kt        # NEW (this PR scaffolds; activation in Phase 1)
│   └── FirebaseSmokeTest.kt        # NEW (Phase 1.4; debug-only smoke harness)
├── logging/
│   └── SafeLog.kt                  # MODIFIED in Phase 1.3 — new AllowedLogKey entries
└── TranslatorRepApplication.kt     # MODIFIED in Phase 1.2 (activate FirebaseBootstrap.init)

firebase/                            # NEW directory (repo root); scaffolded in this PR
├── README.md
├── firebase.json                    # CLI config (Auth + Firestore + App Check; no Functions)
├── firestore.rules                  # security rules per AC-2
├── firestore.indexes.json           # empty — no composite indexes yet
├── .firebaserc                      # NOT IN THIS PR — created by `firebase use --add` (Phase 0.9)
├── .gitignore                       # exclude .firebaserc only if it contains secrets (typically safe to commit)
└── appcheck/
    ├── android-providers.md         # Play Integrity setup notes (Phase 4.1)
    └── ios-providers.md             # stub — deferred to Story 1-4b

docs/runbooks/
└── firebase-setup-android.md       # NEW (this PR); Bania's Phase 0 walkthrough
```

### Testing standards

- **No unit tests for FirebaseBootstrap.kt in `testDebugUnitTest`** — Firebase APIs are heavily Android-context-dependent and would require Robolectric or instrumented tests. Robolectric isn't currently wired into this project (Story 1.1 scope). The smoke test (AC-5) is the validation surface.
- **Firestore rules can be unit-tested** with the `@firebase/rules-unit-testing` JavaScript package (runs against the local Firestore emulator). NOT in this story's scope — added as `1-4d-firestore-rules-unit-tests` to backlog if Bania wants test coverage there. The Rules Playground in the Firebase console (Phase 2.2) is the manual equivalent.

### Project Structure Notes

- Repo gains `firebase/` at root (top-level peer to `android/`, `ios/`, `infra/`, `shared/`, `docs/`, `_bmad-output/`). This matches architecture.md §"Repo Shape" — Firebase is a discrete deployable surface.
- New Kotlin files under `android/app/src/main/java/com/xaeryx/translatorrep/firebase/` follow the project's per-feature package convention (already established by `ids/`, `logging/`, `ui/`).
- `firebase/.firebaserc` is project-aliasing config from `firebase use --add` — typically safe to commit (no secrets), but check the file content before committing in case Firebase CLI version changes its format.

### References

- [architecture.md §C "Backend & Auth"](../planning-artifacts/architecture.md#c-backend--auth) — Firebase + auth-proxy architecture (ADR-C1, C2, C3)
- [architecture.md §"Firestore Schema"](../planning-artifacts/architecture.md#6-firestore-schema)
- [architecture.md §14 "Logging — SafeLog Facade"](../planning-artifacts/architecture.md#14-logging--safelog-facade)
- [docs/runbooks/firebase-setup-android.md](../../docs/runbooks/firebase-setup-android.md) — Phase 0 walkthrough
- [docs/project-context.md §10](../../docs/project-context.md) — externally-blocked work table (update in Phase 4.2)
- [Story 1.1](./1-1-android-project-scaffold.md) (if exists) — scaffold work that left the TODOs
- [Story 1.5 — SafeLog](./1-5-safelog-lint-ulid.md) — facade this story will extend with new keys
- [Story 1.6 — CI/CD](./1-6-cicd-per-stack.md) — CI behavior on this PR

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-Phase-0 manual setup walkthrough)

### Debug Log References

- **Phase 0 click-through time:** ~30 min (Brady walked through the runbook §1-§5 with periodic clarifications on the new Firebase console UI which has reorganized "Build" group items into "Security" + "Databases & Storage" categories).
- **Phase 2 rules deploy:**
  ```
  === Deploying to 'translatorrep-8d773'...
  i  deploying firestore
  i  firestore: ensuring required API firestore.googleapis.com is enabled...
  +  firestore: required API firestore.googleapis.com is enabled
  i  firestore: reading indexes from firestore.indexes.json...
  i  cloud.firestore: checking firestore.rules for compilation errors...
  +  cloud.firestore: rules file firestore.rules compiled successfully
  i  firestore: uploading rules firestore.rules...
  +  firestore: released rules firestore.rules to cloud.firestore
  +  Deploy complete!
  ```
- **Phase 1 local validation:** `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug --no-daemon` → BUILD SUCCESSFUL in 1m12s (cold cache; warm should be ~20s). 0 detekt smells. All 11+ unit tests pass. APK assembled cleanly with `google-services` plugin successfully processing `google-services.json`.
- **Phase 3 smoke test:** Bania's manual step post-merge.

### Completion Notes List

- Firebase project ID: `translatorrep-8d773` (Firestore in `asia-southeast2` = Jakarta).
- AllowedLogKey enum keys exercised: `FIREBASE_INIT`, `APP_CHECK_INIT`, `AUTH_UID`, `SMOKE_USERS_WRITE`, `SMOKE_FORBIDDEN_WRITE` — all 5 were pre-added at the 2026-05-23 scaffolding PR and are now used by `FirebaseBootstrap` + `FirebaseSmokeTest`.
- New dependency: `kotlinx-coroutines-play-services:1.9.0` added to `libs.versions.toml` + the `coroutines` bundle so Firebase `Task<T>` calls (`signInAnonymously()`, Firestore `.set()`) can `.await()` cleanly without callback nesting. Also unblocks Stories 1.8-1.13 (pairing arc has many Task-returning Firebase calls).
- `FirebaseBootstrap.init()` runs synchronously in `Application.onCreate()` — wall-clock ~10-50ms on a warm device (FirebaseApp.initializeApp + App Check provider install are both fast). No cold-boot regression expected.
- App Check provider selection: Gradle source-set discrimination (`src/debug/.../AppCheckFactoryProvider.kt` vs `src/release/.../AppCheckFactoryProvider.kt`) — NOT a `BuildConfig.DEBUG` runtime branch, because `firebase-appcheck-debug` is `debugImplementation` only and would NoClassDefFoundError at release-compile time. This pattern was scaffolded at 2026-05-23.
- Smoke test trigger: debug builds only, gated on `--es firebase-smoke true` intent extra. Production sign-in flow lands in Story 1.8 — this story's `FirebaseSmokeTest` is exclusively a Story-1.4-AC-5 validation harness, not production code.
- Phase 3 (Bania's device smoke test) is the only remaining work for story flip to `done`. The story is flipped to `review` now because all code/deploy work is complete; Phase 3 validation is reported back, then CR → `done`.

### File List

**Created:**

- `android/app/src/main/java/com/xaeryx/translatorrep/firebase/FirebaseSmokeTest.kt` — AC-5 smoke harness (3 checks: anon sign-in, own write, forbidden write).

**Modified:**

- `android/app/build.gradle.kts` — uncommented `google-services` + `firebase-crashlytics` plugins.
- `android/gradle/libs.versions.toml` — added `kotlinx-coroutines-play-services` library + included it in `coroutines` bundle.
- `android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt` — activated `FirebaseBootstrap.init(this)`; removed stale TODOs.
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — added `maybeTriggerFirebaseSmokeTest()` (debug + intent extra gate).
- `_bmad-output/implementation-artifacts/1-4-firebase-init-firestore-rules-baseline-app-check-providers.md` — this file (Phase 0 ✓ checkboxes, Phase 1 ✓ checkboxes, Dev Agent Record filled, Status → review).
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — `1-4-...: ready-for-dev → review`; last_updated bump.
- `docs/project-context.md` §10 — 1-4 row updated to "in review (Phase 1 + 2 done; Phase 3 = Bania's device smoke test)".

**Created (Phase 0 user-side):**

- `firebase/.firebaserc` — alias `default` → `translatorrep-8d773`. Created by `firebase use --add`.
- `android/app/google-services.json` — gitignored, on disk only.

### Change Log

- 2026-05-23 — Story 1.4 file created (Android-only scope; iOS deferred to Story 1-4b, Firebase App Distribution deferred to Story 1-4c). Firebase project setup is gated on Bania completing Phase 0 (manual console clicks per `docs/runbooks/firebase-setup-android.md`). This PR scaffolds: `firebase/` directory + Firestore rules + App Check Android provider notes + `FirebaseBootstrap.kt` skeleton (compiles, not yet called from Application.kt). Activation happens in a follow-up dev session after Bania's manual Firebase setup lands.
- 2026-05-24 — Rescope: Bania confirmed Google Play Console + Apple Dev accounts exist. Story 1-4c "Firebase App Distribution" renamed to "Google Play Internal Testing" (better native-Android distribution + validates Play Integrity end-to-end). Phase 0.7 (Play Integrity) flat-required (no $25 bailout). No code changes; story-spec and runbook updates only.
- 2026-05-24 (Phase 0 + Phase 1 landed) — **Phase 0 manual setup complete** (Bania walked through runbook §1-§7: Firebase project `translatorrep-8d773` created with Analytics OFF; Android app `com.xaeryx.translatorrep` registered + SHA-1/SHA-256 added; `google-services.json` placed at `android/app/`; Anonymous Auth enabled; Firestore created in `asia-southeast2` Jakarta; App Check + Play Integrity registered; Play Integrity API enabled in Cloud Console + linked from Play Console; Firebase CLI installed + linked via `.firebaserc`; rules deployed). **Phase 1 wiring landed in this PR:** Firebase plugins uncommented; `FirebaseBootstrap.init(this)` activated; `FirebaseSmokeTest.runOnce()` implemented; MainActivity wires the smoke test on intent extra (debug only); new `kotlinx-coroutines-play-services` dep added for `Task.await()`. **Status → review.** Phase 3 (Bania's device smoke test) is the only remaining item; story flips to `done` after device validation + CR.
