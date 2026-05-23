# RoomState Derivation Rules

> **Authority:** This file documents how the client-side `RoomState` enum (`active`, `waitingForPartner`, `ended`) is derived from observable LiveKit room state (`room.remoteParticipants`, connection state, participant lifecycle events). Architecture pattern §13 ("State Management") + ADR-A6 ("Leave-and-rejoin within 5-min window") are the source-of-truth.
>
> **Updated:** 2026-05-22 (Story 1.7, satisfies Gap I.13)
> **Versioning:** Bump the document date and add a changelog entry when adding new states. Adding a new state requires an ADR amendment in `architecture.md` (no ad-hoc state additions).

---

## 1. The Three Canonical States

| State | Meaning | When entered |
|---|---|---|
| `active` | Both Paired Users are in the LiveKit room; bidirectional media + Data Channel are flowing. | On `participantConnected` event when `room.remoteParticipants.count == 1` (just the partner). |
| `waitingForPartner` | Local user is in the LiveKit room but the partner has left within the 5-min `empty_timeout` window. Local user MAY rejoin (FR-32). | On `participantDisconnected` event when the disconnected participant was the partner AND the LiveKit server's `empty_timeout: 300` has not yet fired. |
| `ended` | The Call is terminated. Either: both users explicitly ended (single end-Call gesture each), OR the `empty_timeout: 300` fired and the LiveKit room was destroyed. | On `disconnect()` from local end-Call, OR on `roomEnded` event from LiveKit. |

---

## 2. Derivation Rules

### From LiveKit observable state → `RoomState`

```
local.connectionState == .disconnected
    => RoomState.ended

local.connectionState == .connected AND room.remoteParticipants.count == 0
    => RoomState.waitingForPartner
       (partner has left within empty_timeout window; local user MAY rejoin)

local.connectionState == .connected AND room.remoteParticipants.count >= 1
    => RoomState.active
       (room has at least one peer; if count > 1, log a SafeLog event — only 2-person Pairs in v1, so >1 remote is unexpected and warrants attention)
```

### From `RoomState` → UI surface mapping

- `active` → In-Call screen (Audio 40/60 or Video 50/50 layout per FR-26 selection), Caption stack rendering, `AudioCallControlRow` or `VideoCallControlRow` visible.
- `waitingForPartner` → In-Call screen continues to render with `CallWaitingForPartnerState` banner overlay. Captions can still accumulate from local user's own utterances but ARE NOT published to the (absent) peer via Data Channel — they're locally rendered only. End-Call gesture returns to Paired home immediately.
- `ended` → Transition to Paired home screen (or Paired-Empty if the pairing was dissolved mid-Call).

### Side effects on transitions

| Transition | Side effect |
|---|---|
| `active` → `waitingForPartner` | Emit `RejoinNotification` local notification on the LEAVER's device (the one who triggered the disconnect). Start 5-min client-side countdown. |
| `waitingForPartner` → `active` (partner rejoined) | Dismiss `RejoinNotification`. Resume Data Channel publish. Capture/render any captions that the local user spoke during the gap as historical (no replay to peer — peer's view starts fresh from rejoin). |
| `waitingForPartner` → `ended` (5-min timer expired locally OR remote room destroyed) | Dismiss `RejoinNotification`. Transition local UI to Paired home with a quiet "Partner did not return — call ended" snackbar (auto-dismiss 3s). |
| any → `ended` (local end-Call) | Disconnect LiveKit, stop audio capture, signal Translation Pipeline to stop, return to Paired home within 2s (FR-9). |

---

## 3. The Two Sides of the Leave-and-Rejoin Asymmetry

When the LEAVER hangs up but the partner stays in the room:

| Perspective | `RoomState` value | Why |
|---|---|---|
| Leaver | `ended` (they explicitly ended; their LiveKit connection is `.disconnected`) | Symmetric end-Call UI on their side. `RejoinNotification` is shown on the leaver's notification surface to enable rejoin (which itself triggers a NEW LiveKit connect to the SAME `roomName`). |
| Remaining partner | `waitingForPartner` (they observe `participantDisconnected` for the leaver; their `room.remoteParticipants.count == 0` while their `local.connectionState == .connected`) | `CallWaitingForPartnerState` banner overlay; 5-min countdown on the remaining side too. |

This asymmetry is INTENTIONAL per ADR-A6 — both sides have a path to the same outcome (rejoin within 5 min), but the local state-machine reflects their actual local connection state.

---

## 4. Provisional States (architecture §8)

The `RoomState` enum reserves a `provisional.*` namespace for design experiments (e.g., `provisional.partnerTypingButSilent`). Provisional states are time-boxed at 14 days from first commit; the CR agent flags any still-live `provisional.*` past the deadline for ADR-promotion or removal. **No new top-level states without an ADR.**

---

## 5. Anti-Patterns (CR-rejected)

- Deriving `RoomState` from anything OTHER than LiveKit observable state + the leave-and-rejoin timer. No "if user tapped X then state = Y" shortcuts.
- Coupling the Translation Pipeline lifecycle directly to `RoomState` transitions — instead, `CallSession` observes `RoomState` and invokes Provider start/stop. (UI never calls Providers directly; architecture pattern §13.)
- Allowing the UI to render `waitingForPartner` for >5 minutes without the LiveKit room actually destroying — the client-side timer MUST match the server's `empty_timeout: 300`. Integration test required.

---

## 6. Cross-Platform Implementation Notes

- **Android:** `CallSession` exposes `roomState: StateFlow<RoomState>` backed by a private `MutableStateFlow`. Subscribed by the `In-Call` composable via `collectAsStateWithLifecycle()`.
- **iOS:** `CallSession` exposes `roomState: AsyncStream<RoomState>` (or `@Published var roomState: RoomState` on an `ObservableObject`, depending on SwiftUI lifecycle choice at scaffolding). Subscribed by the `In-Call` view via `.onReceive()` or `@StateObject` binding.
- Both platforms MUST emit transitions through the same code paths — no platform-specific RoomState transitions. Cross-platform integration test (the leave-and-rejoin validation in Story 7.7) verifies parity.
