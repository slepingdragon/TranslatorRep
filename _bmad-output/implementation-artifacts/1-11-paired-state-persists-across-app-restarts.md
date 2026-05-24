# Story 1.11: Paired State Persists Across App Restarts (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-1.10 merge). Android-only; iOS mirror later.
     Fourth pairing-arc story. Introduces Room (first use) + the /pairs-membership listener
     (partner-side discovery — the second half of the rules-compatible pairing model).
     THIRD instance of the same rules conflict resolved (see Dev Notes): partner display name
     cannot be read from /users/{partnerUid} (owner-only rule) → defaults to "Partner". -->

## Story

As Bania (or his girlfriend) once paired,
I want my app to skip the pairing UI entirely on subsequent launches and go straight to the Paired home,
so that pairing is a one-time event and the app feels like WhatsApp (just-there).

## Acceptance Criteria

(From epics.md Story 1.11 / FR-4, reconciled with the deployed Firestore rules — see Dev Notes.)

**Given** I have successfully paired,
**When** I kill the app and reopen it,
**Then:**

1. **AC-1 (paired routing):** the Paired home renders (not Paired-Empty). MainActivity routes on `PairingStatus` (`Unknown`→loading / `Unpaired`→Paired-Empty / `Paired`→Paired home), replacing Story 1.10's in-session flag.
2. **AC-2 (offline name):** the partner's display name shows without a network call — defaults to **"Partner"** (FR-23) from the local mirror (display-name *sharing* arrives in Story 8.5; see Dev Notes).
3. **AC-3 (offline partner uid):** the partner UID is recoverable from local storage (**Room mirror**, `PairedPartnerEntity`) without network.
4. **AC-4 (live reconcile):** a real-time Firestore listener keeps local state in sync — implemented as a **`/pairs`-membership listener** (not `/users/{my-uid}/pairId`; see Dev Notes), driving both restart-sync and the immediate post-pair transition.
5. **AC-5 (partner-side discovery):** on first discovery the partner writes its OWN `/users/{uid}.pairId` — completing the rules-compatible pairing model started in Story 1.10.
6. **AC-6 (offline-degraded):** if Firestore is briefly unreachable on launch, the Paired home still renders from the Room mirror (offline-first read before the listener).

**Out of scope (deferred):** the real Paired home (partner-name styling + Call button) is **Story 2.2** — 1.11 keeps the `PairedHomePlaceholder`. Display-name *sharing* is **Story 8.5**. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-6 ✅ and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`. Kill/restart + offline confirmation folds into the post-1.4c QR-install device pass.

## Tasks / Subtasks

### Phase 1 — Room local mirror (first Room use)

- [x] **1.1** `pairing/local/PairedPartnerEntity.kt` (single-row, `id = 0`) + `PairedPartnerDao` (get/upsert/clear) + `PairingDatabase` (`exportSchema=false`, destructive fallback — it's a rebuildable cache).
- [x] **1.2** `pairing/PairingMirror.kt` (seam + `MirroredPair`) + `local/RoomPairingMirror.kt` (DAO-backed impl).

### Phase 2 — Directory seam + status repository

- [x] **2.1** `pairing/PairDirectory.kt` (`RemotePair` + `observePairFor`/`findPairFor`/`ensureOwnPairId`). Impl on `PairingFirestoreRepository`: `callbackFlow` snapshot listener over `Filter.or(memberA==me, memberB==me)`; `ensureOwnPairId` delegates to `setUserPairId`.
- [x] **2.2** `pairing/PairingStatus.kt` (Unknown/Unpaired/Paired) + `pairing/PairingStatusRepository.kt` — offline-first mirror read, then listener `reconcile` (testable): write own pairId on first discovery, preserve cached name (default "Partner"), update mirror; `null` → clear + Unpaired.

### Phase 3 — Wiring

- [x] **3.1** `TranslatorRepApplication`: build `PairingDatabase` (Room) + expose `pairingStatusRepository` (reuses `appScope`).
- [x] **3.2** `MainActivity`: `LaunchedEffect` starts the repo for the signed-in uid; route on `PairingStatus`. Dropped 1.10's `rememberSaveable` flag + `PairedEmptyScreen.onPaired` (the listener drives the transition now). `PairedHomePlaceholder(partnerName)`.

### Phase 4 — Tests + docs

- [x] **4.1** `PairingStatusRepositoryTest` (5): partnerOf, null→Unpaired+clear, fresh→Paired+own-pairId-write+default-name, memberB partner resolution, already-mirrored→no-rewrite+cached-name.
- [x] **4.2** Story file + `sprint-status.yaml` (1-11 → review) + `docs/project-context.md`.

## Dev Notes

### Third rules conflict — partner display name (resolved by the same principle)

AC-2 (epic) says load the partner's name from `/users/{partner-uid}/displayName`. The deployed rules make `/users/{uid}` readable **only by its owner**, so a client cannot read the partner's `/users` doc — the same constraint that drove the Story 1.10 reconciliation. Resolution (consistent with Bania's prior ruling): the partner name **defaults to "Partner"** (FR-23) for now. Display-name *sharing* will go through the `/pairs` doc (both members can read it) when **Story 8.5** (display-name setting) lands — each member writes its own name into its `/pairs` member slot. No display name is set by anyone yet, so "Partner" is the correct v1 behavior regardless.

### Discovery via `/pairs` membership, not `/users/{my-uid}/pairId`

The epic frames the listener as on `/users/{my-uid}/pairId`. That works for the initiator (who wrote its own pairId in 1.10) but **never fires for the partner** (no one wrote the partner's `/users` doc). So the canonical, both-sides-correct source is the `/pairs` doc itself: a `Filter.or(memberA==me, memberB==me)` snapshot listener. On discovery the partner writes its own `/users.pairId` (AC-5) for consistency, but the *driver* is `/pairs`.

### Room vs. a lighter store

The mirror is a single 3-field row, but the AC + architecture explicitly call for a Room/SwiftData mirror, and Room's deps were already provisioned. This is also the app's first Room setup (the encrypted transcript-history DB is a separate Epic-8 SQLCipher database). `exportSchema=false` + destructive fallback because the mirror is always rebuildable from `/pairs`.

### Why the post-pair transition needs no explicit navigation

Firestore offline persistence (on by default) echoes a just-created local `/pairs` doc to the active snapshot listener within milliseconds, so creating the pair in Story 1.10 flips `PairingStatus` to `Paired` and MainActivity re-routes — no `onPaired` callback needed. This let 1.11 delete 1.10's in-session `rememberSaveable` flag and `PairedEmptyScreen.onPaired`, unifying the transition + restart paths through one repository.

### Same testable-seam pattern (Stories 1.8–1.10)

`reconcile` is pure suspend over the `PairDirectory` + `PairingMirror` fakes (5 unit tests). The `start()` Flow/`callbackFlow`/Room wiring is the thin untested wrapper (JUnit4-only toolchain — no Robolectric for Room/listeners). `ensureOwnPairId` is wrapped in `runCatching` so an offline cold-boot can't crash the reconcile.

### Library references

- [Room (KSP) basics](https://developer.android.com/training/data-storage/room) — entity/dao/database.
- [Firestore `Filter.or` queries](https://firebase.google.com/docs/firestore/query-data/queries#or_queries) — membership across two fields.
- [`callbackFlow` for snapshot listeners](https://firebase.google.com/docs/firestore/query-data/listen) + [kotlinx `callbackFlow`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html).
- [Firestore offline persistence](https://firebase.google.com/docs/firestore/manage-data/enable-offline) — default-on; serves cache + echoes local writes.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: route on PairingStatus; drop onPaired flag; PairingLoadingGate
├── TranslatorRepApplication.kt            # MODIFIED: Room DB + pairingStatusRepository
└── pairing/
    ├── PairingStatus.kt                   # NEW
    ├── PairDirectory.kt                   # NEW (RemotePair + seam)
    ├── PairingMirror.kt                   # NEW (MirroredPair + seam)
    ├── PairingStatusRepository.kt         # NEW (reconcile core + start wrapper)
    ├── PairingFirestoreRepository.kt      # MODIFIED: + PairDirectory impl (/pairs listener)
    ├── ui/PairedEmptyScreen.kt            # MODIFIED: drop onPaired (listener drives transition)
    └── local/
        ├── PairedPartnerEntity.kt         # NEW (Room)
        ├── PairedPartnerDao.kt            # NEW (Room)
        ├── PairingDatabase.kt             # NEW (Room — first DB)
        └── RoomPairingMirror.kt           # NEW (PairingMirror impl)
android/app/src/test/java/com/xaeryx/translatorrep/pairing/
└── PairingStatusRepositoryTest.kt         # NEW (5 tests)
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); Room/listener/UI are framework-coupled (no CI instrumented tests). Local validate (JDK 17): `:app:detekt` 0 smells; `PairingStatusRepositoryTest` 5/5 (26 pairing tests total green); `:app:assembleDebug` green (Room KSP codegen clean).

### References

- [epics.md Story 1.11](../planning-artifacts/epics.md) — AC source.
- [Story 1.10](./1-10-enter-partners-pairing-code-to-pair.md) — initiator-side pairing this completes (partner discovery).
- [firebase/firestore.rules](../../firebase/firestore.rules) — `/pairs` member-read + `/users` owner-only rules driving the reconciliation.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.10 merge)

### Debug Log References

- Room KSP codegen verified early (`:app:kspDebugKotlin :app:compileDebugKotlin` BUILD SUCCESSFUL) before the rest.
- Used Room 2.6.1's no-arg `fallbackToDestructiveMigration()` (the `dropAllTables` overload is 2.7+).
- Full validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`PairingStatusRepositoryTest` 5/5); `:app:assembleDebug` BUILD SUCCESSFUL.

### Completion Notes List

- Partner discovery is a `/pairs`-membership listener (`Filter.or`), the both-sides-correct source; the epic's `/users/{my-uid}/pairId` listener is insufficient for the partner.
- Partner name defaults to "Partner" — the partner's `/users` doc is unreadable per rules; name-sharing deferred to Story 8.5 via `/pairs`.
- First Room database in the app; single-row mirror; destructive fallback (rebuildable cache).
- Unified the post-pair transition + restart-routing through one app-wide `PairingStatusRepository`; removed 1.10's in-session flag and `PairedEmptyScreen.onPaired`.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-11-paired-state-persists-across-app-restarts.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingStatus.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairDirectory.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingMirror.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingStatusRepository.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/local/PairedPartnerEntity.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/local/PairedPartnerDao.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/local/PairingDatabase.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/local/RoomPairingMirror.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/pairing/PairingStatusRepositoryTest.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/PairingFirestoreRepository.kt` — + PairDirectory impl (/pairs listener, ensureOwnPairId)
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairedEmptyScreen.kt` — dropped onPaired
- `android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt` — Room DB + pairingStatusRepository
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — route on PairingStatus + PairingLoadingGate + PairedHomePlaceholder(partnerName)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-11 backlog → review; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.11 implemented (Android). Room mirror + `/pairs`-membership listener → app-wide `PairingStatusRepository`; restart routing + offline-degraded Paired home; partner-side own-pairId write completes the rules-compatible pairing model. Partner name defaults to "Partner" (name-sharing → 8.5; rules forbid reading partner `/users`). 5 unit tests; detekt clean; assembleDebug green. Status → `review`.
