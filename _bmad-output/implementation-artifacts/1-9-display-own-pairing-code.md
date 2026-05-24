# Story 1.9: Display Own Pairing Code (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-1.8 merge). Android-only; iOS mirror is a
     future story (after 1.2). Second story of the pairing arc. Builds on the 1.8 auth layer
     (PairingViewModel reads the UID from TranslatorRepApplication.authRepository). -->

## Story

As Bania (or his girlfriend) on first launch when not yet paired,
I want to see my own 6-digit Pairing Code prominently on the Paired-Empty home screen,
so that I can share it with my partner via WhatsApp text.

## Acceptance Criteria

(From epics.md Story 1.9 / FR-2.)

**Given** I am signed in anonymously (Story 1.8) and not paired,
**When** the Paired-Empty home screen renders,
**Then:**

1. **AC-1 (generate + persist):** a 6-digit decimal Pairing Code is generated client-side and persisted at `/codes/{6digit}` with `{ownerId, createdAt, expiresAt}`. **Field is `ownerId`** (not `ownerUid`) to match the deployed `firebase/firestore.rules` — see Dev Notes "schema drift".
2. **AC-2 (collision check):** the code is collision-checked at generation time; on collision, **one digit is regenerated** and re-checked (`PairingCodeGenerator.withOneDigitChanged` + the `PairingCodeAllocator` loop). Unit-tested.
3. **AC-3 (display, UX-DR14):** rendered via `PairingCodeDisplay` — 6 digits in `Display` typography (`displayLarge` = 44sp with generous tracking) above a "Share this with your partner" hint in Footnote (`labelSmall`).
4. **AC-4 (tap to copy):** tapping the code copies it to the clipboard and shows a "Code copied" snackbar.
5. **AC-5 (long-press regenerate):** long-pressing the code reveals a "Regenerate code" menu item that allocates a new code and **invalidates the prior one** (deletes the old `/codes/{old}` doc) in Firestore.
6. **AC-6 (validity):** the code remains valid until used in a successful pairing or explicitly regenerated. A returning user keeps their existing code (`CodeStore.findOwnedCode` reuse) rather than minting a new one each launch.
7. **AC-7 (position, UX-DR15):** the code display sits **below** the partner-input field — the D4b "partner-input-first" Paired-Empty layout.

**Out of scope (deferred):** the partner-code **input is presentational only** in 1.9 (empty state + disabled Pair button, establishing the D4b layout per AC-7). The interactive `PairingCodeInput` — numeric keyboard, digit states, Firestore lookup, the "Pair" transition, inline errors — is **Story 1.10**. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-7 ✅ and local validate (detekt + unit tests + assembleDebug) is green → CR pass → `done`. On-device confirmation (code renders, copy works, regenerate replaces the doc) folds into the post-1.4c QR-install device pass.

## Tasks / Subtasks

### Phase 1 — Core (unit-testable, no Android deps)

- [x] **1.1** `pairing/PairingCodeGenerator.kt` — 6-digit RNG; `generate()` + `withOneDigitChanged()`; seedable `Random` for deterministic tests.
- [x] **1.2** `pairing/PairingFirestoreRepository.kt` — `CodeStore` interface seam + `PairingFirestoreRepository` impl (`/codes/{code}`; `ownerId` field; `createdAt`=serverTimestamp; `expiresAt`=+365d).
- [x] **1.3** `pairing/PairingCodeAllocator.kt` — `obtain` (reuse-or-allocate), `allocate` (generate → collision-check → one-digit-retry → persist, capped), `regenerate` (delete old + allocate).

### Phase 2 — ViewModel

- [x] **2.1** `pairing/PairingViewModel.kt` — `PairingCodeUiState` (Loading / Ready(code) / Error) + `PairingViewModel` (obtain on init, `retry()`, `regenerate()`) + `Factory(ownerUid, allocator)`. Thin wrapper over the allocator (no own unit test — `viewModelScope` needs coroutines-test which isn't in the toolchain; the allocator carries the tested logic).

### Phase 3 — UI (UX-DR13/14/15)

- [x] **3.1** `pairing/ui/PairingCodeDisplay.kt` — UX-DR14 panel; tap → copy, long-press → "Regenerate code" `DropdownMenu`; single a11y node.
- [x] **3.2** `pairing/ui/PairingCodeInput.kt` — UX-DR13 **presentational placeholder** (empty state + disabled Pair; interaction is Story 1.10).
- [x] **3.3** `pairing/ui/PairedEmptyScreen.kt` — D4b layout (input top → divider → own-code below); owns clipboard write + "Code copied" snackbar; Loading/Ready/Error rendering; stateless `PairedEmptyContent` for previews.
- [x] **3.4** `MainActivity` — route `AuthState.SignedIn` → `PairedEmptyRoute` (builds `PairingViewModel` via factory + Firestore-backed allocator).

### Phase 4 — Tests + docs

- [x] **4.1** `PairingCodeGeneratorTest` (3) + `PairingCodeAllocatorTest` (5, in-memory `FakeCodeStore`): format, determinism, one-digit retry, collision→retry, reuse, regenerate-deletes-old, exhausted-retry-throws.
- [x] **4.2** Story file + `sprint-status.yaml` (1-9 → review) + `docs/project-context.md`.

## Dev Notes

### Schema drift: `ownerId` vs `ownerUid` (important)

The epic AC and architecture §"Firestore Schema" say the `/codes/{code}` doc field is `ownerUid`. The **deployed** `firebase/firestore.rules` (Story 1.4) checks **`ownerId`**:

```
allow create: if request.auth != null
  && request.resource.data.ownerId is string
  && request.resource.data.ownerId == request.auth.uid;
```

The deployed rules are the runtime source of truth — writing `ownerUid` would be rejected with `PERMISSION_DENIED`. So `PairingFirestoreRepository` writes **`ownerId`**. The rules use `ownerId` consistently across create/read/update/delete, so this is internally consistent; only the prose specs drifted. (Not "fixing" the rules to `ownerUid` — that would mean redeploying live security rules against the project for a cosmetic rename. Flagged here for the iOS mirror, which must also use `ownerId`.)

### `expiresAt` policy

UX spec §Open-items: a code is "valid until explicitly regenerated" — i.e. **no time-based expiry** in v1's UX. The schema still carries `expiresAt` and Story 1.10 has a "Code expired" branch, so the field is populated at **createdAt + 365 days** — effectively non-expiring for the WhatsApp-share flow, while leaving a Firestore-TTL/cleanup hook and a non-null value for 1.10's check. Constant `CODE_VALIDITY_DAYS`.

### The testable seam (same pattern as Story 1.8)

JUnit4-only toolchain (no Robolectric/MockK). The collision/reuse/regenerate logic that's worth testing lives in `PairingCodeAllocator` (pure suspend) over the `CodeStore` interface; tests use an in-memory `FakeCodeStore` + a seeded `PairingCodeGenerator` via `runBlocking`. The Firestore SDK is only touched by `PairingFirestoreRepository` (untested, thin). The `PairingViewModel` is also a thin untested wrapper — `viewModelScope` needs `kotlinx-coroutines-test` (`Dispatchers.setMain`) which isn't in the toolchain, so testable logic deliberately sits below it.

### Reuse on launch — why query, not /users

`obtain()` reuses an existing code via `CodeStore.findOwnedCode` (a `whereEqualTo("ownerId", uid).limit(1)` query — permitted by the `allow read: if request.auth != null` rule). This keeps Story 1.9 inside the `/codes` collection and avoids coupling to `/users/{uid}` (the local Room mirror + `pairId` is Story 1.11's concern). A returning user keeps the code their partner may already hold (AC-6).

### Clipboard + snackbar live in the screen, not the component/ViewModel

`PairingCodeDisplay` stays presentational + Context-free; `PairedEmptyScreen` owns the `LocalClipboardManager` write and the `SnackbarHostState`. The snackbar uses `SnackbarDuration` default (Short ≈ 4s); the AC's "2-second" is a soft target — not worth a custom timer for v1.

### Why the partner-input is a placeholder here

AC-7 only requires the own-code to sit *below the partner-input field*, establishing the D4b layout. The input's interactivity (keyboard, validation, lookup, pairing transition, errors) is the whole of Story 1.10, so 1.9 ships the legitimate empty state (placeholder digits + disabled Pair) and 1.10 replaces it with the stateful component.

### Library references

- [Firestore Android (KTX) reads/writes](https://firebase.google.com/docs/firestore/query-data/get-data) — `get().await()`, `whereEqualTo`, `set`, `delete`.
- [`combinedClickable`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary#(androidx.compose.ui.Modifier).combinedClickable) — tap + long-press.
- [Compose `viewModel(factory =)`](https://developer.android.com/jetpack/compose/libraries#viewmodel) — lifecycle-viewmodel-compose.
- [`LocalClipboardManager`](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary#LocalClipboardManager()) — clipboard write.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: SignedIn → PairedEmptyRoute
└── pairing/
    ├── PairingCodeGenerator.kt            # NEW
    ├── PairingFirestoreRepository.kt      # NEW (CodeStore interface + Firestore impl)
    ├── PairingCodeAllocator.kt            # NEW
    ├── PairingViewModel.kt                # NEW (+ PairingCodeUiState + Factory)
    └── ui/
        ├── PairingCodeDisplay.kt          # NEW (UX-DR14)
        ├── PairingCodeInput.kt            # NEW (UX-DR13 placeholder; 1.10 makes it interactive)
        └── PairedEmptyScreen.kt           # NEW (D4b layout)
android/app/src/test/java/com/xaeryx/translatorrep/pairing/
├── PairingCodeGeneratorTest.kt            # NEW (3 tests)
└── PairingCodeAllocatorTest.kt            # NEW (5 tests)
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); UI is preview-backed (no instrumented tests in CI — 1.6d scaffold deferred).
- Local validate (JDK 17): `:app:detekt :app:testDebugUnitTest :app:assembleDebug` → all green; detekt 0 smells; 8 new tests pass.

### References

- [epics.md Story 1.9](../planning-artifacts/epics.md) — AC source.
- [Story 1.8](./1-8-anonymous-sign-in.md) — auth layer + the seam/StateFlow pattern reused here.
- [ux-design-specification.md §PairingCodeDisplay / §D4b](../planning-artifacts/ux-design-specification.md) — UX-DR13/14/15.
- [firebase/firestore.rules](../../firebase/firestore.rules) — `/codes/{code}` rules (`ownerId`).

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.8 merge)

### Debug Log References

- Local validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`PairingCodeGeneratorTest` 3/3, `PairingCodeAllocatorTest` 5/5); `:app:assembleDebug` BUILD SUCCESSFUL.

### Completion Notes List

- Used `ownerId` (deployed-rules field name), not the spec's `ownerUid` — documented above; iOS mirror must match.
- Collision retry implemented as FR-2's literal "one digit regenerated and re-checked", capped at 10 attempts (throws rather than overwrite — impossible at 2-user scale anyway).
- Returning users reuse their existing code (`findOwnedCode`) — code stability for the partner.
- Partner-input is a presentational placeholder; the interactive component is Story 1.10.
- No pairing-code logging (no suitable `AllowedLogKey`, and a code is semi-secret — adding a key would force an iOS PR).

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-9-display-own-pairing-code.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingCodeGenerator.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingFirestoreRepository.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingCodeAllocator.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingViewModel.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairingCodeDisplay.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairingCodeInput.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairedEmptyScreen.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingCodeGeneratorTest.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingCodeAllocatorTest.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — route SignedIn → PairedEmptyRoute (builds PairingViewModel)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-9 backlog → review; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.9 implemented (Android). Own 6-digit pairing code generated/persisted at `/codes/{code}` (collision-checked, reused-on-return, regenerable), displayed via `PairingCodeDisplay` (UX-DR14) on the D4b Paired-Empty home (UX-DR15), tap-to-copy + long-press-regenerate. Used `ownerId` per deployed rules. 8 unit tests green; detekt clean; assembleDebug green. Status → `review`.
