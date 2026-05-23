# Story 1.4: Firebase Init + Firestore Rules Baseline + App Check Providers (Android)

Status: ready-for-dev

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

- [ ] **0.1** Sign in to [console.firebase.google.com](https://console.firebase.google.com) with the Google account that will own the project.
- [ ] **0.2** Create a new Firebase project named `TranslatorRep` (Analytics optional — recommend OFF for a privacy-first app).
- [ ] **0.3** Add an Android app to the project: package name `com.xaeryx.translatorrep`; nickname `TranslatorRep Android`; optionally provide SHA-1 from debug keystore (`./gradlew :app:signingReport` or via Android Studio).
- [ ] **0.4** Download `google-services.json` from the Firebase console → place at `android/app/google-services.json`. Verify it's gitignored (`git status` should NOT show it).
- [ ] **0.5** Enable **Authentication → Sign-in method → Anonymous → Enable**.
- [ ] **0.6** Enable **Firestore Database → Create database → start in production mode** (we'll deploy our own rules). Pick a region close to Indonesia (asia-southeast2 = Jakarta if available, else asia-southeast1 = Singapore).
- [ ] **0.7** Enable **App Check** for the Android app. Register **Play Integrity** as the attestation provider — requires Google Play Console + Play Integrity API enabled in Google Cloud Console for the linked project. Runbook §5 has the full walkthrough including the $25 Play Console fee + project linking.
- [ ] **0.8** (Optional) Generate a Debug App Check token via the Firebase console for use with `DebugAppCheckProviderFactory` during dev — saves needing real Play Integrity attestation in debug builds. Store the token in `local.properties` as `firebaseAppCheckDebugToken=...` (gitignored).
- [ ] **0.9** Install Firebase CLI globally: `npm install -g firebase-tools`; authenticate via `firebase login`; link the local `firebase/` directory to the new project via `cd firebase && firebase use --add <project-id>`.

### Phase 1 — Android code wiring (after Phase 0 complete)

- [ ] **1.1** Uncomment the two Firebase plugins in [`android/app/build.gradle.kts`](../../android/app/build.gradle.kts) (lines 8–10 currently): `alias(libs.plugins.google.services)` + `alias(libs.plugins.firebase.crashlytics)`. Run `./gradlew :app:assembleDebug` once to verify the `google-services` plugin successfully processes `google-services.json` (will fail with a clear error if the file is missing or malformed).
- [ ] **1.2** Activate the dormant `FirebaseBootstrap.init(this)` call in [`TranslatorRepApplication.onCreate()`](../../android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt) (uncomment the line; the function already exists, scaffolded in this PR). Remove the now-stale `TODO Story 1.4` comment lines.
- [ ] **1.3** Verify `FirebaseBootstrap` correctly selects `DebugAppCheckProviderFactory` in debug builds and `PlayIntegrityAppCheckProviderFactory` in release (BuildConfig.DEBUG gate). Code already scaffolded in this PR.
- [ ] **1.4** Implement `FirebaseSmokeTest.runOnce(context)` — calls `signInAnonymously()` (suspending; use lifecycleScope from Application is wrong, so launch from MainActivity or a dedicated DebugViewModel) → on success writes `/users/{uid}` smoke doc → on success attempts forbidden write to `/users/<other-uid>` and asserts it's rejected. SafeLog all stages. Wire from a debug-only menu entry or just call directly from MainActivity.onCreate when `BuildConfig.DEBUG && intent.hasExtra("firebase-smoke")`.

### Phase 2 — Firestore rules deploy

- [ ] **2.1** Deploy rules via `cd firebase && firebase deploy --only firestore:rules`. Capture deploy command output in Dev Agent Record. Visit Firebase console → Firestore → Rules tab to verify the rules match the deployed file.
- [ ] **2.2** Manually test rule enforcement in Firebase console → Firestore → Rules → "Rules Playground": simulate `read /users/foo` as auth uid `foo` → allow; simulate same read as uid `bar` → deny. Repeat for `/pairs/x` with memberA=alice, memberB=bob — Alice reads ✓, Carol reads ✗.

### Phase 3 — App Check verification + smoke test

- [ ] **3.1** Run debug APK on a real Android device (Play Integrity requires Play Services; emulator works if Play Services is installed on the AVD image). Verify SafeLog emits `firebase_init=success` and `app_check_init=debug` (or `=playintegrity` in a release-build smoke).
- [ ] **3.2** Trigger the FirebaseSmokeTest from MainActivity → verify (a) anonymous sign-in produces a Firebase UID (logged via SafeLog `auth_uid=<first-4-chars>` — never log full UIDs per canonical-names.md), (b) Firestore write to own `/users/{uid}` succeeds, (c) Firestore write to another `/users/<other-uid>` is rejected with `PERMISSION_DENIED`.
- [ ] **3.3** Record smoke-test device + observed SafeLog events in Dev Agent Record → Debug Log References.

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

_(filled in at implementation time)_

### Debug Log References

_(filled in at implementation time — Phase 3 smoke test results, Phase 2 rules deploy command output, any unexpected Firebase API errors + their resolutions)_

### Completion Notes List

_(filled in at implementation time — record: Firebase project ID (just the slug, no secret), Phase 0 click-through time observed, FirebaseBootstrap init wall-clock observed on cold app start, Play Integrity vs Debug provider verification, smoke-test device + Android version, any AllowedLogKey enum entries added)_

### File List

_(filled in at implementation time)_

### Change Log

- 2026-05-23 — Story 1.4 file created (Android-only scope; iOS deferred to Story 1-4b, Firebase App Distribution deferred to Story 1-4c). Firebase project setup is gated on Bania completing Phase 0 (manual console clicks per `docs/runbooks/firebase-setup-android.md`). This PR scaffolds: `firebase/` directory + Firestore rules + App Check Android provider notes + `FirebaseBootstrap.kt` skeleton (compiles, not yet called from Application.kt). Activation happens in a follow-up dev session after Bania's manual Firebase setup lands.
