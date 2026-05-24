# Story 1.10: Enter Partner's Pairing Code to Pair (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-1.9 merge). Android-only; iOS mirror later.
     Third pairing-arc story. Makes the 1.9 PairingCodeInput placeholder interactive. Two
     spec-vs-deployed-rules conflicts confirmed with Bania 2026-05-24 (see Dev Notes). -->

## Story

As Bania (or his girlfriend),
I want to enter my partner's 6-digit Pairing Code on the Paired-Empty home and have my app transition to the Paired home,
so that I can establish the Paired Users relationship and start having translated Calls.

## Acceptance Criteria

(From epics.md Story 1.10 / FR-3, reconciled with the deployed Firestore rules — see Dev Notes.)

**Given** I am on the Paired-Empty home and my partner has shared their 6-digit code,
**When** I focus the `PairingCodeInput` field and tap "Pair",
**Then:**

1. **AC-1 (UX-DR13 input):** a single 6-char numeric field — `text-primary` digits over a `— — — — — —` placeholder, large hit targets (≥48dp), native numeric keypad on focus.
2. **AC-2 (Pair gating):** "Pair" is disabled until exactly 6 digits are entered (and during submit).
3. **AC-3 (lookup):** submitting resolves `/codes/{code}` to the owner UID (`CodeStore.lookup` → `CodeRecord`).
4. **AC-4 (inline errors within ~2s):** not-found → "Code not found"; expired (`expiresAt` past) → "Code expired"; owned-by-self → "That's your own code". Errors render below the input and clear on the next edit.
5. **AC-5 (pair creation):** on a valid code, `/pairs/{pairId}` is created with `{memberA, memberB, createdAt}` (`pairId` = ULID; `memberA` = me). **The caller's own** `/users/{uid}.pairId` is written.
6. **AC-6 (transition):** my app transitions to the Paired home on success (in-session; restart-persistence is Story 1.11).
7. **AC-7 (own code untouched):** an inline error never invalidates my own code — the own-code panel below is unaffected.

**Reconciled with the deployed rules (confirmed with Bania 2026-05-24):** the epic AC also says "**both** users' `/users/{uid}/pairId` are updated." The deployed `firestore.rules` let a client write **only its own** `/users` doc, so the initiator writes only its own `pairId`; the **partner discovers the pair via a `/pairs`-membership listener (Story 1.11)** and writes its own `pairId` there. This story implements the initiator side.

**Out of scope (deferred):** partner-side discovery + the real Paired home (partner name, Call button = Story 2.2) + restart-persistent paired routing (Story 1.11). The "Paired home" target here is a minimal placeholder. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-7 ✅ and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`. Two-device pairing confirmation folds into the post-1.4c QR-install device pass (also exercises the 1.11 partner-discovery once built).

## Tasks / Subtasks

### Phase 1 — Data layer

- [x] **1.1** `PairingFirestoreRepository`: add `CodeStore.lookup(code): CodeRecord?` (ownerId + expiresAt millis) + a `PairStore` seam (`createPair`, `setUserPairId`) implemented over `/pairs` + `/users` (own doc only). New `CodeRecord` type.

### Phase 2 — Pairing logic (testable core)

- [x] **2.1** `pairing/PairingCoordinator.kt` — `pair(myUid, code)` → `PairResult` (Success(pairId) / NotFound / OwnCode / Expired). Validation order not-found → own-code → expired. On success: ULID `pairId`, `createPair(memberA=me, memberB=owner)`, `setUserPairId(me)`. Injected `pairIdFactory` + `now` for tests.

### Phase 3 — ViewModel + UI

- [x] **3.1** `PairingViewModel`: `enteredCode` + `onCodeChange` (digits-only, max 6, clears error on edit), `inputState` (`PairingInputUiState`: Editing/Submitting/Error/Paired), `pair()`. Factory gains the coordinator.
- [x] **3.2** `PairingCodeInput` → interactive: `BasicTextField` (NumberPassword keypad, ImeAction.Done), placeholder dashes, ≥48dp targets, inline error (`StateRed`), Pair button with submit spinner.
- [x] **3.3** `PairedEmptyScreen`: wire the input flows; `LaunchedEffect` fires `onPaired(pairId)` on `Paired`; `imePadding` + `verticalScroll` for the keyboard.
- [x] **3.4** `MainActivity`: build the coordinator (one repository serves both seams); `PairedEmptyRoute(onPaired)`; SignedIn branch tracks `pairId` (rememberSaveable) → `PairedHomePlaceholder` on success.

### Phase 4 — Tests + docs

- [x] **4.1** `PairingCoordinatorTest` (5): NotFound, OwnCode, Expired, Success (asserts pair created with memberA=caller + only own pairId written), null-expiresAt-non-expiring. Patched the allocator-test fake for the new `lookup`.
- [x] **4.2** Story file + `sprint-status.yaml` (1-10 → review) + `docs/project-context.md`.

## Dev Notes

### Two spec-vs-deployed-rules conflicts (both confirmed with Bania 2026-05-24)

1. **`ownerId` vs `ownerUid`** — the deployed rules use `ownerId` for `/codes`; the prose specs say `ownerUid`. Kept `ownerId` (rules = runtime truth; carried from Story 1.9).
2. **"Both users' pairId"** — the AC literally asks the initiator to write the partner's `/users` doc, which the rules forbid (`/users/{uid}` writable only by `uid`). Resolution: initiator writes only its own side (`/pairs` + own `pairId`); the partner discovers via a `/pairs`-membership listener (Story 1.11). This is why `PairStore` has **no** partner-write method and the `PairingCoordinator` `Success` path writes a single `setUserPairId`.

### Validation order: not-found → own-code → expired

Own-code is checked before expiry so a user who fat-fingers their *own* code never sees the confusing "Code expired" — they get "That's your own code". A `null` `expiresAt` (legacy/missing field) is treated as non-expiring (defensive; v1 codes don't hard-expire anyway).

### Same testable-seam pattern (Stories 1.8/1.9)

`PairingCoordinator` is pure suspend over the `CodeStore` + `PairStore` fakes with injected `pairIdFactory`/`now`, so all four outcomes + the exact writes are unit-tested without Firestore. The ViewModel stays a thin untested `viewModelScope` wrapper (no `kotlinx-coroutines-test` in the toolchain). `pairId` = `UlidGenerator::next` (architecture: ULID-or-Firebase-auto ids).

### In-session transition only (1.10), persistence is 1.11

On success, `MainActivity` flips a `rememberSaveable` `pairId` and shows `PairedHomePlaceholder`. This survives config changes but not process death — restart-persistent routing (reading `/users/{uid}.pairId` + a Room mirror + the `/pairs` listener) is Story 1.11. The `/users/{uid}.pairId` write here is exactly what 1.11 will read.

### Library references

- [Firestore `get`/`set`/`SetOptions.merge`](https://firebase.google.com/docs/firestore/manage-data/add-data#set_a_document) — `/pairs` create + `/users` merge.
- [Compose `BasicTextField` + `KeyboardOptions`/`KeyboardActions`](https://developer.android.com/develop/ui/compose/text/user-input) — numeric keypad + ImeAction.Done.
- [`Modifier.imePadding`](https://developer.android.com/develop/ui/compose/layouts/insets) — keep the field above the keyboard.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: coordinator wiring + paired transition + PairedHomePlaceholder
└── pairing/
    ├── PairingFirestoreRepository.kt      # MODIFIED: CodeRecord + CodeStore.lookup + PairStore seam/impl
    ├── PairingCoordinator.kt              # NEW (PairResult + PairingCoordinator)
    ├── PairingViewModel.kt                # MODIFIED: input state + pair() + coordinator in Factory
    └── ui/
        ├── PairingCodeInput.kt            # MODIFIED: placeholder → interactive (UX-DR13)
        └── PairedEmptyScreen.kt           # MODIFIED: wire input flows + onPaired transition
android/app/src/test/java/com/xaeryx/translatorrep/pairing/
├── PairingCoordinatorTest.kt             # NEW (5 tests)
└── PairingCodeAllocatorTest.kt           # MODIFIED: fake gains lookup() override
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); UI preview-backed (no CI instrumented tests). Local validate (JDK 17): `:app:detekt` 0 smells; `PairingCoordinatorTest` 5/5 (+ allocator 5/5, generator 3/3 still green); `:app:assembleDebug` green.

### References

- [epics.md Story 1.10](../planning-artifacts/epics.md) — AC source.
- [Story 1.9](./1-9-display-own-pairing-code.md) — the `PairingCodeInput` placeholder + `/codes` schema this builds on.
- [firebase/firestore.rules](../../firebase/firestore.rules) — `/codes` (read-any), `/pairs` (member-only), `/users` (owner-only) rules that drove the reconciliation.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.9 merge)

### Debug Log References

- Local validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`PairingCoordinatorTest` 5/5, `PairingCodeAllocatorTest` 5/5, `PairingCodeGeneratorTest` 3/3); `:app:assembleDebug` BUILD SUCCESSFUL.

### Completion Notes List

- Reconciled the "both users' pairId" AC with the owner-only `/users` rule: initiator writes its own side; partner discovers via a `/pairs` listener in 1.11. `PairStore` deliberately has no partner-write.
- Kept `ownerId` (deployed-rules field) for the `/codes` lookup.
- Validation order not-found → own-code → expired; null expiresAt = non-expiring.
- Paired transition is in-session (rememberSaveable) to a placeholder; real Paired home = 2.2, restart-persistence = 1.11.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-10-enter-partners-pairing-code-to-pair.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingCoordinator.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingCoordinatorTest.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingFirestoreRepository.kt` — CodeRecord + CodeStore.lookup + PairStore seam/impl
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingViewModel.kt` — input state + pair() + coordinator in Factory
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairingCodeInput.kt` — placeholder → interactive
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairedEmptyScreen.kt` — wire input flows + onPaired
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — coordinator wiring + paired transition + PairedHomePlaceholder
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingCodeAllocatorTest.kt` — fake gains lookup()
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-10 backlog → review; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.10 implemented (Android). Interactive partner-code input → lookup `/codes` → create `/pairs/{pairId}` (ULID) + write own `pairId` → transition to Paired-home placeholder. Reconciled "both pairId" + `ownerId` with deployed rules (partner-discovery deferred to 1.11). 5 new unit tests; detekt clean; assembleDebug green. Status → `review`.
