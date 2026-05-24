# Story 1.8: Anonymous Sign-In on First Launch (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session). Android-only; the iOS mirror is a
     separate future story (sequenced after Story 1.2 lands the Xcode project on Mac),
     consistent with the 1-2 / 1-4b / 1-6b iOS-deferral pattern. First story of the
     pairing arc (1.8–1.13); establishes the app-wide auth layer the arc reuses. -->

## Story

As a new user (Bania or his girlfriend),
I want the app to sign me in silently on first launch with no login UI,
so that I can use the app without creating an account or remembering a password.

## Acceptance Criteria

(From epics.md Story 1.8 / FR-1. X25519 keypair generation is explicitly **out of scope** here — deferred to Story 1.12, per the epic.)

**Given** the app is installed and never previously launched,
**When** I open the app for the first time,
**Then:**

1. **AC-1 (sign-in kicked off before UI):** Firebase Auth `signInAnonymously()` is initiated from `Application.onCreate` (before `MainActivity` composes its first frame). MainActivity renders a branded loading surface bound to `AuthState.SigningIn` until a UID is established.
2. **AC-2 (stable UID, no account UI):** a stable anonymous UID is established and exposed app-wide as `StateFlow<AuthState>` → `AuthState.SignedIn(uid)`. No login, signup, or account UI is ever shown in any state (loading spinner + retry-on-failure only).
3. **AC-3 (UID persists across kills/restarts):** on a returning launch the locally-cached Firebase session resolves the UID with **no network call** (`FirebaseAuthGateway.currentUid()` non-null → `SignedIn` immediately). Unit-tested: the cached-session path asserts `signInCallCount == 0`.
4. **AC-4 (privacy-safe logging):** sign-in outcomes log via `SafeLog.event(AllowedLogKey.AUTH_UID, …)` carrying only the first 4 UID chars on success (never the full UID) or `failed:<ExceptionClass>` on failure. No `CALL_ID` / conversation content is logged (no Call exists yet). Unit-tested.
5. **AC-5 (resilient first boot):** a cold-boot sign-in failure (e.g. no connectivity) never crashes the app — it lands in `AuthState.Failed(reason)` and MainActivity offers a "Try again" affordance that re-runs sign-in.

**Out of scope (deferred):** X25519 identity keypair generation → **Story 1.12**. Pairing-code UI → **Story 1.9+**. iOS anonymous sign-in → future iOS story (after Story 1.2).

**Done criteria:** flips to `review` when AC-1..AC-5 ✅ and local validate (detekt + unit tests + assembleDebug) is green → CR pass → `done`. Real-device confirmation (kill/restart → same UID; airplane-mode → Failed+retry) folds into the same post-1.4c QR-install device pass that gates Story 1.4.

## Tasks / Subtasks

### Phase 1 — Auth core (unit-testable, no Android deps)

- [x] **1.1** `pairing/AuthState.kt` — sealed interface `SigningIn | SignedIn(uid) | Failed(reason)`. Deliberately not a Firebase type (keeps consumers backend-agnostic + JVM-testable).
- [x] **1.2** `pairing/FirebaseAuthGateway.kt` — `interface` (the fake-able seam: `currentUid()` + `suspend signInAnonymously()`) + `FirebaseAuthGatewayImpl` (real `FirebaseAuth`, `signInAnonymously().await()`).
- [x] **1.3** `pairing/AnonymousAuthRepository.kt` — private `MutableStateFlow<AuthState>` + public `StateFlow`; `suspend ensureSignedIn()` (idempotent; existing-session fast path; sign-in; failure → `Failed`, never throws). Logging injected (`logEvent`, default `SafeLog::event`) so the core stays off the JVM-test Android path.

### Phase 2 — Wiring

- [x] **2.1** `TranslatorRepApplication` — `appScope` (`SupervisorJob + Dispatchers.Default`), `authRepository` (lazy, `FirebaseAuthGatewayImpl`), and `appScope.launch { authRepository.ensureSignedIn() }` right after `FirebaseBootstrap.init`. Removes the `// TODO Story 1.8` placeholder.
- [x] **2.2** `MainActivity` — reads `authRepository` off the Application, `collectAsStateWithLifecycle()`, renders `AuthGateScreen` (SigningIn → spinner; SignedIn → placeholder home; Failed → message + "Try again"). Wordmark "TranslatorRep" always shown. 1.4 AC-5 smoke trigger retained.

### Phase 3 — Tests

- [x] **3.1** `test/.../pairing/AnonymousAuthRepositoryTest.kt` — 8 JUnit4 tests via `runBlocking` + `FakeAuthGateway`: initial state, fresh sign-in, cached-session-no-network, failure→Failed, retry-after-failure, idempotency, 4-char-only UID logging, failure-label logging.

### Phase 4 — Docs + tracking

- [x] **4.1** Story file (this).
- [x] **4.2** `sprint-status.yaml`: 1-8 `backlog` → `review`; `last_updated` bump.
- [x] **4.3** `docs/project-context.md` — pairing-arc status note.

## Dev Notes

### Why an Application-scoped repository, not a ViewModel

FR-1 wants the UID established *before any UI renders*, and every downstream surface (pairing arc, then Calls) needs it. A screen-scoped `ViewModel` is the wrong lifetime — sign-in must outlive any single screen and be ready at cold boot. So the auth state lives in an Application-held `AnonymousAuthRepository` (manual singleton; no DI framework, matching `object FirebaseBootstrap` / `object SafeLog`). The Story 1.9+ `PairingViewModel` will read the resolved UID from this repository rather than calling Firebase Auth itself.

### Why the `FirebaseAuthGateway` seam exists

The project's unit-test toolchain is **JUnit 4 only** — no Robolectric, no MockK (`libs.versions.toml`). The repository's sign-in logic (existing-session vs. fresh vs. failure) is the part worth testing, but it can't touch `FirebaseAuth` on the plain JVM. `FirebaseAuthGateway` is the minimal fake-able boundary; tests inject `FakeAuthGateway`, production injects `FirebaseAuthGatewayImpl`. This also matches the repo's "wrap the SDK, callers never touch it directly" convention (cf. `UlidGenerator`, `SafeLog`).

### Why logging is injected

`SafeLog.event` routes to `android.util.Log` (debug) / Crashlytics (release) — both unavailable/awkward on the JVM test path. Injecting `logEvent: (AllowedLogKey, Any) -> Unit` (default `SafeLog::event`) lets the repository log in production while tests capture events in a list and assert the **privacy rule** (4 UID chars only) directly, with the real `SafeLog` never loaded into a test.

### Why reuse `AllowedLogKey.AUTH_UID` (no new key)

Adding an `AllowedLogKey` requires an ADR amendment + a *simultaneous iOS PR* (enforced by CR; see `AllowedLogKey.kt` / architecture §14). `AUTH_UID` already exists (added in Story 1.4) and already carries the "4-char prefix or `failed:…`" convention from `FirebaseSmokeTest`, so the production sign-in path reuses it — no cross-platform key churn for an Android-only story.

### `ensureSignedIn()` idempotency + the retry race

`ensureSignedIn()` returns early if already `SignedIn`, so the `Application.onCreate` call and a user-tapped retry can't double-sign-in. The retry affordance only renders in the `Failed` state, so it can never be tapped while a sign-in is in flight — no `Mutex` needed at 2-user scale.

### Library references

- [Firebase Anonymous Auth (Android)](https://firebase.google.com/docs/auth/android/anonymous-auth) — `signInAnonymously()`, session persistence.
- [`collectAsStateWithLifecycle`](https://developer.android.com/topic/libraries/architecture/compose#use-stateflow) — lifecycle-aware StateFlow collection (lifecycle-runtime-compose, already in the `lifecycle` bundle).
- [kotlinx-coroutines-play-services `await()`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/) — bridges Firebase `Task<T>` to `suspend`.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── TranslatorRepApplication.kt        # MODIFIED: appScope + authRepository + launch ensureSignedIn
├── MainActivity.kt                    # MODIFIED: AuthGateScreen state-gated render (no login UI)
└── pairing/                           # NEW package (FR-1..5 per architecture tree)
    ├── AuthState.kt                   # NEW
    ├── FirebaseAuthGateway.kt         # NEW (interface + FirebaseAuthGatewayImpl)
    └── AnonymousAuthRepository.kt     # NEW
android/app/src/test/java/com/xaeryx/translatorrep/
└── pairing/AnonymousAuthRepositoryTest.kt   # NEW (8 tests)
```

### Testing standards

- Pure-JVM JUnit4 unit tests (no instrumented test required for the auth logic — the Compose gate render is covered by the deferred instrumented scaffold from 1.6d, not run in CI).
- `runBlocking` (coroutines-core, on the test classpath) substitutes for the absent `kotlinx-coroutines-test` `runTest`. StateFlow asserted synchronously via `.value` after the suspend call returns.

### References

- [epics.md Story 1.8](../planning-artifacts/epics.md) — AC source.
- [Story 1.4](./1-4-firebase-init-firestore-rules-baseline-app-check-providers.md) — Firebase init + `FirebaseSmokeTest` (sign-in call shape reused).
- [architecture.md §"State Management"](../planning-artifacts/architecture.md) — StateFlow on Android; pairing/ package tree.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.6c merge)

### Debug Log References

- Local validate (JDK 17, Microsoft OpenJDK): `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug --no-daemon` → all BUILD SUCCESSFUL. detekt **0 code smells**. `AnonymousAuthRepositoryTest`: **8 tests, 0 skipped, 0 failures, 0 errors**.

### Completion Notes List

- Auth layer added under `pairing/` (per architecture's "pairing/ # FR-1..5" grouping — no separate `auth/` package).
- App-wide auth state is an Application-held `AnonymousAuthRepository` exposing `StateFlow<AuthState>`; sign-in kicked off in `onCreate` so the UID is (usually) ready before first frame.
- `FirebaseAuthGateway` seam + injected logger keep the sign-in core unit-testable under the JUnit4-only toolchain.
- Persist-across-restart is the cached-session fast path (no network); failure is non-fatal with a retry affordance.
- Reused `AllowedLogKey.AUTH_UID`; no new log key (would force an iOS PR).

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-8-anonymous-sign-in.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/AuthState.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/FirebaseAuthGateway.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/AnonymousAuthRepository.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/AnonymousAuthRepositoryTest.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt` — appScope + authRepository + launch ensureSignedIn
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — AuthGateScreen state-gated render
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-8 backlog → review; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.8 implemented (Android). Anonymous sign-in on first launch via an Application-scoped `AnonymousAuthRepository` (`StateFlow<AuthState>`), a fake-able `FirebaseAuthGateway` seam, and a state-gated `MainActivity` (no login UI). 8 unit tests green; detekt clean; assembleDebug green. Status → `review`.
