# Story 2.3: Place an Audio Call (Android)

Status: review

<!-- Created 2026-05-24. Wires the REAL call now that the backend is live (LiveKit Cloud +
     the Render auth-proxy, Story 2.1 Phase 2 done by Bania). Replaces the Story-2.2
     LiveKitRoomManager scaffold with a real connection. NEEDS on-device verification (two
     phones) — SDK/network code can't be behavior-unit-tested on plain JVM. -->

## Story

As Bania (or his girlfriend) on the Paired home,
I want tapping "Call" to actually connect us to a shared LiveKit room with my mic published,
so that we can have a real audio call (the substrate the translation pipeline rides on).

## Acceptance Criteria

(From epics.md Story 2.3 / FR-6.)

**Given** I'm paired and tap "Call",
**Then:**

1. **AC-1 (mic permission):** if `RECORD_AUDIO` isn't granted, the runtime permission is requested; on grant the call proceeds, on deny it doesn't.
2. **AC-2 (token):** the client gets a **Firebase ID token + App Check token** and `POST`s them with `callType:"audio"` + the partner's `peerUid` to `{AUTH_PROXY_BASE_URL}/v1/token` (LiveKit Cloud + Render, Story 2.1). On 200 it receives `livekitJwt` + `livekitWsUrl` + `roomName`.
3. **AC-3 (connect + publish):** `room.connect(livekitWsUrl, livekitJwt)` then `localParticipant.setMicrophoneEnabled(true)` → `CallSession` emits `RoomState.ACTIVE`.
4. **AC-4 (deterministic room):** both partners resolve the **same room** (server derives `roomName` from the sorted UID pair — auth-proxy `roomNameForPair`), so whoever calls first, they land together.
5. **AC-5 (end):** ending the call cancels the flow → `room.disconnect()` (via `onCompletion` / `CallSession.endCall`). A token/connect failure → `RoomState.ENDED` (the connecting screen shows it; End returns home).
6. **AC-6 (boundary):** the UI never touches LiveKit — only `CallSession` → `RoomManager` (`LiveKitRoomManager`) does (Patterns §13).

**Out of scope / deferred:** the full Audio 40/60 **In-Call screen** (UX-DR16) is **Story 2.7** (this uses the minimal connecting shell); incoming-call (FCM/ConnectionService) is **2.4/2.5**; rich room events (peer-left, network-drop, reconnect, leave-and-rejoin) are **Epic 7**; **E2EE** Insertable Streams are **Epic 5** (media is DTLS-SRTP until then). iOS → future.

**Done criteria:** flips to `review` on compile + `assembleDebug` green; → `done` after **Bania's two-device test**: both open the app (paired) → one taps Call → both are in the room with audio. (See "Device test" below.)

## Tasks / Subtasks

- [x] **1.1** Deps/config: `okhttp` (4.12.0) + `AUTH_PROXY_BASE_URL` `buildConfigField` (`https://translatorrep-auth-proxy.onrender.com` — non-secret; WS URL comes back in the token response).
- [x] **2.1** `call/livekit/LiveKitTokenFetcher.kt` — Firebase ID + App Check tokens → `POST /v1/token` (OkHttp + org.json) → `TokenResult.Success(jwt, wsUrl, roomName)` / `Failure(errorCode)` (maps the proxy's `ERR_*`).
- [x] **3.1** Threaded `peerUid`: `RoomManager.connect(callType, peerUid)` + `CallSession.startCall(callType, peerUid)` (+ fake + test updated).
- [x] **3.2** `call/livekit/LiveKitRoomManager.kt` real connect: token → `LiveKit.create(appContext)` → `room.connect(wsUrl, jwt)` → `setMicrophoneEnabled(true)` → emit `ACTIVE`, hold until cancelled; `onCompletion`/`disconnect()` → `room.disconnect()`.
- [x] **4.1** UI: `CallConnectingScreen(peerUid)`; `MainActivity.PairedRoute(partnerUid, partnerName)` builds `CallSession(LiveKitRoomManager(appContext))` + a `RECORD_AUDIO` permission launcher gating the Call.
- [x] **5.1** Validate (detekt 0 + 53 tests + assembleDebug green) + story/tracking.

## Dev Notes

### Why this story is test-light (and that's correct)

2.3 is **SDK + network + Firebase-token glue**: `LiveKit.create/connect`, OkHttp, `getIdToken`/`getAppCheckToken`. None is behavior-unit-testable on plain JVM (the toolchain has no Robolectric/MockK, and these need a device + the live backend). Per the established pattern, the **tested core is the seam above** — `CallSession` (2.2 tests) over the `RoomManager` boundary — and `LiveKitRoomManager`/`LiveKitTokenFetcher` are the thin untested wrappers, verified by **compile (`assembleDebug` against the LiveKit AAR)** + **on-device test**. No new unit tests; the `CallSession` test was updated for the `peerUid` signature.

### LiveKit API: connect + hold, no event stream (yet)

The first attempt used `room.events.collect {}` to map LiveKit events → `RoomState`; LiveKit's event type doesn't expose a plain `Flow.collect`, and rich event handling (peer-left, drop, reconnect) is really Epic 7. So 2.3 uses only the core, stable 2.x calls — `LiveKit.create`, `room.connect`, `localParticipant.setMicrophoneEnabled`, `room.disconnect` — emits `ACTIVE` on a successful connect and `awaitCancellation()`s until the user ends (flow cancel → `onCompletion` → disconnect). `CancellationException` is rethrown (not swallowed) so teardown is clean.

### Client only needs the auth-proxy URL

The token response carries `livekitWsUrl`, so the app config is just `AUTH_PROXY_BASE_URL` (committed `buildConfigField`, non-secret — it's a public endpoint gated by Firebase Auth + App Check; the only secret, the LiveKit API secret, lives only on Render). `peerUid` is threaded from `PairingStatus.Paired.partnerUid` through `PairedRoute` → `startCall`.

### Device test (Bania — this is the `done` gate)

1. Build/install the debug APK on **both** phones (latest `android-ci` artifact, or `./gradlew :app:installDebug`).
2. Both sign in + are paired (Stories 1.8–1.13).
3. On one phone tap **Call** → grant the mic prompt → it should show "Connected" (RoomState.ACTIVE). The other phone: tapping Call joins the same room (deterministic room name). Talk → you should hear each other.
4. Tap **End** → returns to the Paired home.
5. If it shows "Call ended" immediately: the auth-proxy rejected the token — check Render logs (App Check debug token registered? the pair exists in Firestore?). LiveKit Cloud → Sessions should show the room/participants when it works.

### Library references

- [shared/auth-proxy-api.md](../../shared/auth-proxy-api.md) — `POST /v1/token` contract.
- [LiveKit Android — connecting](https://docs.livekit.io/home/quickstarts/android/) — `LiveKit.create` / `room.connect` / `setMicrophoneEnabled`.
- [OkHttp](https://square.github.io/okhttp/) — the token request.
- [Firebase App Check token (Android)](https://firebase.google.com/docs/app-check/android/custom-resource) + ID token.

### Source-tree placement

```
android/app/
├── build.gradle.kts                       # MODIFIED: okhttp dep + AUTH_PROXY_BASE_URL buildConfigField
├── src/main/java/com/xaeryx/translatorrep/
│   ├── MainActivity.kt                     # MODIFIED: PairedRoute(partnerUid) + mic-permission launcher + appContext CallSession
│   └── call/
│       ├── callSession/{RoomManager,CallSession}.kt  # MODIFIED: connect/startCall(callType, peerUid)
│       ├── livekit/LiveKitTokenFetcher.kt  # NEW (token fetch + POST)
│       ├── livekit/LiveKitRoomManager.kt   # MODIFIED: scaffold → real connect
│       └── ui/CallConnectingScreen.kt      # MODIFIED: peerUid param
android/gradle/libs.versions.toml           # MODIFIED: + okhttp
android/app/src/test/.../call/callSession/CallSessionTest.kt  # MODIFIED: peerUid in fake + test
```

### Testing standards

- Pure-JVM JUnit4 for the seam (`CallSessionTest` 2/2). SDK/network glue verified by compile + device. Local validate (JDK 17): `:app:detekt` 0 smells; 53 unit tests green; `:app:assembleDebug` green (compiles against the LiveKit AAR + OkHttp).

### References

- [epics.md Story 2.3](../planning-artifacts/epics.md) — AC source.
- [Story 2.2](./2-2-paired-home-call-button-callsession-scaffolding.md) — the CallSession seam this fills in.
- [Story 2.1](./2-1-auth-proxy-deploy-livekit-cloud-render.md) — the deployed auth-proxy this calls.
- [Story 2.7](../planning-artifacts/epics.md) — the real In-Call screen (this uses the connecting shell).

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24)

### Debug Log References

- First `assembleDebug` failed: `room.events.collect` unresolved (LiveKit's event type isn't a plain `Flow`). Reworked to connect + `awaitCancellation()` (no event stream) — recompiled clean.
- Local validate (JDK 17): `:app:detekt` 0 smells; `:app:testDebugUnitTest` 53 green (`CallSessionTest` 2/2 with the new `peerUid` signature); `:app:assembleDebug` green (LiveKit AAR + OkHttp).

### Completion Notes List

- Real call wired end-to-end: mic permission → token fetch (ID + App Check) → `POST /v1/token` → `room.connect` + publish mic → `ACTIVE`.
- Test-light by nature (SDK/network/Firebase glue); the tested seam is `CallSession`. **Requires Bania's two-device test to flip to `done`** — there may be a runtime fix iteration (App Check token, LiveKit audio session) once tested.
- Reused `AllowedLogKey.ROOM_STATE`; only `AUTH_PROXY_BASE_URL` is new client config (non-secret).

### File List

**Created:**
- `_bmad-output/implementation-artifacts/2-3-place-an-audio-call.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/call/livekit/LiveKitTokenFetcher.kt`

**Modified:**
- `android/app/build.gradle.kts` — okhttp dep + `AUTH_PROXY_BASE_URL` buildConfigField
- `android/gradle/libs.versions.toml` — + okhttp
- `android/app/src/main/java/com/xaeryx/translatorrep/call/livekit/LiveKitRoomManager.kt` — scaffold → real connect
- `android/app/src/main/java/com/xaeryx/translatorrep/call/callSession/RoomManager.kt` + `CallSession.kt` — `connect`/`startCall(callType, peerUid)`
- `android/app/src/main/java/com/xaeryx/translatorrep/call/ui/CallConnectingScreen.kt` — `peerUid` param
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — `PairedRoute(partnerUid)` + mic-permission launcher + appContext `CallSession`
- `android/app/src/test/java/com/xaeryx/translatorrep/call/callSession/CallSessionTest.kt` — `peerUid` in fake + test
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 2-3 → review; last_updated bump
- `docs/project-context.md` — Epic-2 status note

### Change Log

- 2026-05-24 — Story 2.3 implemented (Android). Real audio-call placement: mic permission → Firebase ID + App Check tokens → `POST /v1/token` (LiveKit Cloud + Render) → `room.connect` + publish mic → `RoomState.ACTIVE`. SDK/network glue (test-light; `CallSession` seam tested). detekt 0 + 53 tests + assembleDebug green. **Needs Bania's two-device test to flip to `done`.** Status → `review`.
