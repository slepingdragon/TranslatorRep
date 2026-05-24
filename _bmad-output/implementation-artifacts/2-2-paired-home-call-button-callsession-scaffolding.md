# Story 2.2: Paired Home with Call Button + CallSession Scaffolding (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-Epic-1-pairing-arc). Android-only; iOS later.
     FIRST Epic-2 story (epic-2 → in-progress). Establishes the CallSession orchestration seam.
     Scaffolding: the real LiveKit connection is Story 2.3 (needs LiveKit Cloud + auth-proxy host;
     Oracle is OUT). Buildable + testable now without a live SFU. -->

## Story

As Bania (or his girlfriend) once paired,
I want a Paired home screen with a prominent Call button and the orchestration layer that will own the LiveKit room lifecycle,
so that I have a one-tap entry into a Call and the codebase has the architectural seam (`CallSession`) for future captions/E2EE/video to plug into.

## Acceptance Criteria

(From epics.md Story 2.2.)

**Given** I am paired and on the Paired home,
**When** the screen renders,
**Then:**

1. **AC-1 (partner name):** the partner display name (default "Partner") is centered at top.
2. **AC-2 (Call button, UX-DR38):** a single "Call" primary action (filled glass pill, ≥48dp, full `text-primary` label) is centered below the partner name.
3. **AC-3 (audio in this epic):** the button initiates an Audio Call (the two-button `CallTypeSelector` arrives in Epic 6 per UX-DR17).
4. **AC-4 (CallSession seam):** `CallSession` exists in `call/callSession/` exposing `startCall(callType: CallType): Flow<RoomState>` (Patterns §1/§13).
5. **AC-5 (RoomState):** `RoomState` enum (`active`, `waitingForPartner`, `ended`) is wired (`waitingForPartner` unused until Epic 7).
6. **AC-6 (LiveKit ownership):** `LiveKitRoomManager` is owned by `CallSession`; the UI never calls LiveKit APIs directly (Patterns §13) — enforced via the `RoomManager` seam.
7. **AC-7 (tap → startCall):** tapping Call invokes `CallSession.startCall(.audio)` — full place-call mechanics are Story 2.3.

**Out of scope (deferred):** the real LiveKit connection (auth-proxy JWT → `room.connect`) is **Story 2.3**; the Audio 40/60 In-Call screen (UX-DR16) is **Story 2.7**; incoming-call (FCM/ConnectionService) is **Story 2.5**. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-7 ✅ and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`.

## Tasks / Subtasks

### Phase 1 — Call domain types + seam

- [x] **1.1** `call/CallType.kt` (`AUDIO`/`VIDEO`, `wireName`) + `call/callSession/RoomState.kt` (`ACTIVE`/`WAITING_FOR_PARTNER`/`ENDED`, `wireName`).
- [x] **1.2** `call/callSession/RoomManager.kt` — the fake-able seam (`connect(callType): Flow<RoomState>`, `disconnect()`); the only thing allowed to touch LiveKit (Patterns §13).

### Phase 2 — Orchestration

- [x] **2.1** `call/callSession/CallSession.kt` — owns a `RoomManager`; `startCall(callType)` delegates to `connect` + logs each `RoomState` (`AllowedLogKey.ROOM_STATE`); `endCall()` → `disconnect()`.
- [x] **2.2** `call/livekit/LiveKitRoomManager.kt` — `RoomManager` scaffold (`connect` = `emptyFlow()` with a Story-2.3 TODO for the real auth-proxy-JWT → `room.connect` → event-map; `disconnect` TODO).

### Phase 3 — UI + wiring

- [x] **3.1** `pairing/ui/PairedHomeScreen.kt` → real Paired home: partner name centered top + UX-DR38 Call pill (glass `MonochromeGlassPanel`, pill corner, ≥56dp, clickable) centered + the Story-1.13 Settings gear/sheet retained. New `onCall` param.
- [x] **3.2** `call/ui/CallConnectingScreen.kt` — minimal in-call shell: collects `startCall(.audio)`, shows the `RoomState` ("Connecting…" until 2.3 emits), End button.
- [x] **3.3** `MainActivity` — `PairedRoute` toggles Paired home ↔ connecting screen on Call; holds the `CallSession(LiveKitRoomManager())`.

### Phase 4 — Tests + docs

- [x] **4.1** `call/callSession/CallSessionTest` (2): `startCall` connects with the type + surfaces the manager's `RoomState` stream + logs each transition; `endCall` delegates to disconnect.
- [x] **4.2** Story file + `sprint-status.yaml` (epic-2 → in-progress; 2-2 → review) + `docs/project-context.md`.

## Dev Notes

### The CallSession seam (why this story matters beyond a button)

`CallSession` is the single orchestration layer between UI and the realtime/translation providers (Patterns §1/§13). Everything call-time plugs into it later: captions (Epic 3/4), E2EE keyProvider (Epic 5), video tracks (Epic 6), leave-and-rejoin (Epic 7). Enforcing "UI never touches LiveKit" now (via the `RoomManager` seam, with `LiveKitRoomManager` as the only SDK-facing class) keeps that boundary clean from the start. `startCall(): Flow<RoomState>` is the observable contract the In-Call screen will consume.

### Scaffold, not a live call (Oracle is OUT)

The real connection is Story 2.3 and needs a LiveKit endpoint. **Oracle was dropped** — the SFU path is **LiveKit Cloud**, with the auth-proxy on a small host (Fly.io/Render/Cloud Run); both become config when 2.3 wires `LiveKitRoomManager.connect`. Until then `connect` returns `emptyFlow()` and the connecting screen stays "Connecting…" with an End affordance — an honest scaffold (the AC defers place-call mechanics to 2.3).

### Testable seam (same pattern as Epic 1)

`CallSession` is unit-tested over a fake `RoomManager` (scripted `Flow<RoomState>`); `LiveKitRoomManager` (SDK) + the Compose screens are the thin untested wrappers. Reused the existing `AllowedLogKey.ROOM_STATE` (no new key → no iOS-PR requirement).

### UX-DR38 Call pill

Built from the existing `MonochromeGlassPanel` primitive (Thick intensity, pill corner radius, `clickable`) rather than a raw Material `Button` — keeps the monochrome-glass aesthetic and reuses the established surface. Label is `titleLarge` `text-primary`; hit target ≥56dp.

### Library references

- [Architecture Patterns §1/§13](../planning-artifacts/architecture.md) — `CallSession` naming + "UI never calls providers directly".
- [LiveKit Android SDK](https://docs.livekit.io/client-sdk-android/) — `Room`, `RoomEvent` (consumed in Story 2.3).
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html) — the `startCall` contract.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: PairedRoute (home ↔ connecting) + CallSession
├── call/                                  # NEW package (Epic 2)
│   ├── CallType.kt                        # NEW
│   ├── callSession/
│   │   ├── RoomState.kt                   # NEW
│   │   ├── RoomManager.kt                 # NEW (seam)
│   │   └── CallSession.kt                 # NEW (orchestration)
│   ├── livekit/LiveKitRoomManager.kt      # NEW (RoomManager scaffold; SDK-facing)
│   └── ui/CallConnectingScreen.kt         # NEW (minimal in-call shell)
└── pairing/ui/PairedHomeScreen.kt         # MODIFIED: real Paired home (partner name + Call pill)
android/app/src/test/java/com/xaeryx/translatorrep/call/callSession/
└── CallSessionTest.kt                     # NEW (2 tests)
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking` + `Flow.toList`); UI preview-backed. Local validate (JDK 17): `:app:detekt` 0 smells; `CallSessionTest` 2/2 (**47 unit tests total app-wide green**); `:app:assembleDebug` green.

### References

- [epics.md Story 2.2](../planning-artifacts/epics.md) — AC source.
- [Story 1.13](./1-13-settings-sheet-shell-with-unpair.md) — the Paired home Settings gear/sheet this builds on.
- [Story 2.3 (next)](../planning-artifacts/epics.md) — wires the real LiveKit connection into `LiveKitRoomManager`.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-Epic-1 pairing arc)

### Debug Log References

- Local validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`CallSessionTest` 2/2; 47 total); `:app:assembleDebug` BUILD SUCCESSFUL.

### Completion Notes List

- Established the `CallSession` orchestration seam + the `RoomManager` boundary (only `LiveKitRoomManager` touches the SDK).
- Real Paired home (partner name + UX-DR38 Call pill from the glass primitive); Settings/unpair from 1.13 retained.
- Scaffold only: `connect` is `emptyFlow()` (real LiveKit-Cloud connection = Story 2.3). Reused `AllowedLogKey.ROOM_STATE`.
- First Epic-2 story; epic-2 → in-progress.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/2-2-paired-home-call-button-callsession-scaffolding.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/call/CallType.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/callSession/RoomState.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/callSession/RoomManager.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/callSession/CallSession.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/livekit/LiveKitRoomManager.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/ui/CallConnectingScreen.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/call/callSession/CallSessionTest.kt`

**Modified:**
- `android/app/src/main/java/com/xaeryx/translatorrep/pairing/ui/PairedHomeScreen.kt` — real Paired home (partner name + Call pill); kept Settings/unpair
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — PairedRoute (home ↔ connecting) + CallSession wiring
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — epic-2 → in-progress; 2-2 → review; last_updated bump
- `docs/project-context.md` — Epic-2 status note

### Change Log

- 2026-05-24 — Story 2.2 implemented (Android). Real Paired home (partner name + UX-DR38 Call pill) + the `CallSession` orchestration seam (`startCall(): Flow<RoomState>`, owns `RoomManager`/`LiveKitRoomManager`; UI never touches LiveKit). Scaffold — real LiveKit-Cloud connection is Story 2.3. 2 unit tests; detekt clean; assembleDebug green. First Epic-2 story. Status → `review`.
