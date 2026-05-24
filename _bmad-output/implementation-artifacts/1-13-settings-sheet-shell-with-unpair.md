# Story 1.13: Settings Sheet Shell with Unpair (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-1.12 merge). Android-only; iOS mirror later.
     LAST story of the Epic-1 pairing arc. Completes the user-facing pairing flow. -->

## Story

As Bania (or his girlfriend) once paired,
I want a Settings entry from any non-In-Call screen that opens a sheet with an Unpair option using two-tap confirmation,
so that I can dissolve the pairing if needed (rare; v1 has only one partner).

## Acceptance Criteria

(From epics.md Story 1.13.)

**Given** I am on the Paired home,
**When** I tap the top-right Settings gear icon,
**Then:**

1. **AC-1 (sheet, UX-DR35):** a Settings sheet slides up using `ModalBottomSheet` (Android).
2. **AC-2 (contents):** the sheet has an "Unpair from {partner display name}" row plus a placeholder section for future Settings items (Epic 8).
3. **AC-3 (two-tap confirm):** Unpair uses two-tap confirmation — the first tap arms a confirm step ("This disconnects you both and can't be undone." + a destructive **Unpair** button / **Cancel**); the second tap confirms.
4. **AC-4 (unpair effects):** confirming deletes `/pairs/{pairId}`, clears local pairing state (the Room mirror) + the caller's own `/users/{uid}.pairId`, and navigates back to the Paired-Empty home.
5. **AC-5 (partner side):** the partner's `/pairs` listener fires and silently flips their status to Unpaired (no notification) — already handled by Story 1.11's `reconcile(null)`.

**Out of scope (deferred):** the gear on the **Paired-Empty** home (Unpair is irrelevant there; the sheet would only show the Epic-8 placeholder) — added when Epic 8 settings exist. The real Paired home (partner styling + Call button) is **Story 2.2**. Epic-8 settings rows (theme, display name, privacy, transcript history) fill the placeholder later. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-5 ✅ and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`. **This closes the Epic-1 pairing arc** (Stories 1.8–1.13). Two-device unpair confirmation folds into the post-1.4c QR-install device pass.

## Tasks / Subtasks

### Phase 1 — Unpair data ops

- [x] **1.1** `PairDirectory`: + `deletePair(pairId)` + `clearOwnPairId(myUid)`. Impl on `PairingFirestoreRepository` (`/pairs/{pairId}.delete()`; `/users/{uid}.update(pairId, FieldValue.delete())`). Both owner/member-permitted by the deployed rules.
- [x] **1.2** `PairingStatusRepository`: + `unpair(myUid, pairId)` (fire-and-forget on `scope`) + `performUnpair` (testable): clear mirror → set `Unpaired` (optimistic) → best-effort delete pair + clear own pairId. The live listener also observes the deletion and reconciles.

### Phase 2 — Settings sheet UI

- [x] **2.1** Added `androidx.compose.material:material-icons-core` (BOM-managed) for the Settings gear.
- [x] **2.2** `pairing/ui/PairedHomeScreen.kt` — Paired-home body (placeholder, real one = 2.2) + top-right Settings `IconButton` (gear); `ModalBottomSheet` with the two-tap-confirm Unpair row + an Epic-8 placeholder section.
- [x] **2.3** `MainActivity`: `PairingStatus.Paired` → `PairedHomeScreen(partnerName, onUnpair = { pairingRepository.unpair(uid, pairId) })`. Removed the inline `PairedHomePlaceholder`. Unpair flips status → Unpaired → re-routes to `PairedEmptyRoute`.

### Phase 3 — Tests + docs

- [x] **3.1** `PairingStatusRepositoryTest`: + `performUnpair` test (clears mirror, reports Unpaired, deletes pair + own pairId); fake gains `deletePair`/`clearOwnPairId`.
- [x] **3.2** Story file + `sprint-status.yaml` (1-13 → review; **epic-1 pairing arc complete**) + `docs/project-context.md`.

## Dev Notes

### Unpair is one-sided + listener-driven (consistent with the rules model)

The unpairing client deletes `/pairs/{pairId}` (member-permitted) and clears its OWN `/users/{uid}.pairId` (owner-permitted) — it does NOT touch the partner's `/users` doc (rules forbid it, same as Stories 1.10/1.11). The partner's `/pairs`-membership listener observes the deletion → `reconcile(null)` → their status flips to Unpaired automatically, no notification (AC-5). So unpair needed no new partner-side code — 1.11's reconcile already handles the `null` case.

### Optimistic local flip + the listener

`performUnpair` sets `Unpaired` + clears the mirror immediately (snappy UX), then does the Firestore deletes. The active `observePairFor` listener will also fire `null` (Firestore echoes the local delete) → `reconcile(null)` → Unpaired + mirror clear (idempotent). Firestore deletes are wrapped in `runCatching` so an offline unpair still flips the UI; the deletes replay when back online via Firestore's write queue.

### Two-tap confirmation (AC-3)

Implemented in-sheet (no nested dialog): the "Unpair from {name}" row (first tap) arms a confirm state showing the consequence text + a destructive **Unpair** button (`StateRed`) and **Cancel** (second tap confirms / aborts). Lower-friction than an `AlertDialog` over a `ModalBottomSheet` and avoids the double-scrim.

### Why `material-icons-core` (new dep)

UX-DR35 calls for a Settings gear; `material3` doesn't bundle icons. `material-icons-core` (curated set incl. `Settings`) is BOM-managed (no extra version pin) and will be reused for Call controls etc. The heavier `material-icons-extended` was NOT added (huge; only needed for niche glyphs).

### Same testable-seam pattern

`performUnpair` is pure suspend over the `PairDirectory` + `PairingMirror` fakes (unit-tested). The sheet UI + `unpair` (scope launch) are the thin untested wrappers.

### Library references

- [Material3 `ModalBottomSheet`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#ModalBottomSheet) — experimental; `onDismissRequest` + sheet content.
- [Firestore `FieldValue.delete()`](https://firebase.google.com/docs/firestore/manage-data/delete-data#fields) — remove a single field.
- [material-icons-core `Icons.Filled.Settings`](https://developer.android.com/reference/kotlin/androidx/compose/material/icons/Icons).

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: Paired → PairedHomeScreen(onUnpair); removed inline placeholder
└── pairing/
    ├── PairDirectory.kt                   # MODIFIED: + deletePair / clearOwnPairId
    ├── PairingFirestoreRepository.kt      # MODIFIED: impl deletePair / clearOwnPairId
    ├── PairingStatusRepository.kt         # MODIFIED: + unpair / performUnpair
    └── ui/PairedHomeScreen.kt             # NEW (Settings gear + ModalBottomSheet + two-tap unpair)
android/app/src/test/java/com/xaeryx/translatorrep/pairing/
└── PairingStatusRepositoryTest.kt         # MODIFIED: + performUnpair test; fake gains the two ops
android/gradle/libs.versions.toml          # MODIFIED: + material-icons-core (compose bundle)
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); UI preview-backed. Local validate (JDK 17): `:app:detekt` 0 smells; `PairingStatusRepositoryTest` 6/6 (**45 unit tests total app-wide green**); `:app:assembleDebug` green.

### References

- [epics.md Story 1.13](../planning-artifacts/epics.md) — AC source.
- [Story 1.11](./1-11-paired-state-persists-across-app-restarts.md) — the `PairingStatusRepository` + `/pairs` listener this extends; partner-side unpair is its `reconcile(null)`.
- [firebase/firestore.rules](../../firebase/firestore.rules) — `/pairs` member-delete + `/users` owner-write rules.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.12 merge)

### Debug Log References

- Local validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`PairingStatusRepositoryTest` 6/6; 45 tests total); `:app:assembleDebug` BUILD SUCCESSFUL (benign "unable to strip .so" debug-build info only).

### Completion Notes List

- Unpair is one-sided + listener-driven — no new partner-side code (1.11's `reconcile(null)` handles the partner's flip to Unpaired).
- Optimistic local flip + `runCatching` Firestore deletes (offline-safe; replay via Firestore write queue).
- Two-tap confirm implemented in-sheet (no nested dialog).
- Added `material-icons-core` (BOM-managed) for the gear; not the heavy extended set.
- **Closes the Epic-1 pairing arc (Stories 1.8–1.13).**

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-13-settings-sheet-shell-with-unpair.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairedHomeScreen.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairDirectory.kt` — + deletePair / clearOwnPairId
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingFirestoreRepository.kt` — impl those two ops
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingStatusRepository.kt` — + unpair / performUnpair
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — Paired → PairedHomeScreen(onUnpair); removed inline placeholder
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingStatusRepositoryTest.kt` — + performUnpair test
- `android/gradle/libs.versions.toml` — + material-icons-core (compose bundle)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-13 backlog → review; epic-1 pairing arc complete; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.13 implemented (Android). Settings `ModalBottomSheet` (UX-DR35) from the Paired home with two-tap-confirm Unpair → delete `/pairs/{pairId}` + clear Room mirror + own `pairId`; partner flips to Unpaired via the existing `/pairs` listener. 1 new unit test (45 total); detekt clean; assembleDebug green. **Closes the Epic-1 pairing arc.** Status → `review`.
