# Error Code Registry

> **Authority:** This file mirrors [architecture.md §10 "Error-Code Registry"](../_bmad-output/planning-artifacts/architecture.md#10-error-code-registry). Codes are stable string identifiers; locked now to prevent future analytics-dashboard refactors. Adding a new code requires an ADR amendment + simultaneous Kotlin/Swift PR (same as canonical-name rename policy).
>
> **Updated:** 2026-05-22 (Story 1.7)

---

## 1. Code Format

`ERR_<DOMAIN>_<CONDITION>` SCREAMING_SNAKE_CASE.

Severities:

- `ERR_*` — error; user-visible failure surface OR fatal (Call ends).
- `WARN_*` — warning; recoverable / soft-fail; UI surfaces a calm indicator.
- `INFO_*` — informational; expected state transitions worth logging (first-time-only states, lifecycle events).

---

## 2. Registry

| Code | Severity | Maps to (state taxonomy §7) | UI surface | Fatal? |
|---|---|---|---|---|
| `ERR_TRANS_PROVIDER_UNAVAIL` | error | `translationFailed` | `TranslationUnavailableMarker` (amber) | No — Caption marked failed, conversation continues |
| `ERR_TRANS_PROVIDER_TIMEOUT` | error | `translationFailed` | `TranslationUnavailableMarker` (amber) | No |
| `ERR_ASR_INIT_FAILED` | error | (fatal, surfaces as crash) | n/a | Yes |
| `WARN_ASR_LOW_CONFIDENCE` | warn | `asrLowConfidence` | Inline tertiary marker on Caption row | No |
| `INFO_SUNDANESE_PLACEHOLDER` | info | `sundaneseClause` | `SundanesePlaceholderRow` | No |
| `ERR_NETWORK_DROPPED` | error | `networkDropped` | Offline indicator + soft retry banner | No — LiveKit auto-reconnect |
| `ERR_E2EE_KEY_EXCHANGE_FAILED` | error | (fatal, surfaces; Call ends) | Inline error, return to Paired home | Yes |
| `WARN_E2EE_KEY_NOT_READY` | warn | `e2eeKeyNotReady` | One-time `E2EEKeyExchangeIndicator` | No |
| `WARN_VIDEO_TRACK_SUSPENDED` | warn | `videoPaused` | `VideoPausedTile` (neutral grey, NOT amber) | No — audio continues, auto-retry every 5s |
| `INFO_MODEL_LOADING` | info | `modelLoading` | "Preparing translator" one-time indicator | No |
| `INFO_WAITING_FOR_PARTNER` | info | `waitingForPartner` | `CallWaitingForPartnerState` banner | No |
| `ERR_LIVEKIT_ROOM_FAILED` | error | (fatal, Call ends) | Native call-end + "Call ended unexpectedly" | Yes |
| `ERR_PAIRING_CODE_INVALID` | error | (UI inline) | Inline error under `PairingCodeInput` | No |
| `ERR_PAIRING_CODE_EXPIRED` | error | (UI inline) | Inline error under `PairingCodeInput` | No |

---

## 3. Usage Conventions

- **Logging:** every error/warn/info code is logged via `SafeLog.event(AllowedLogKey.ERROR_CODE, "ERR_*")` along with `call_id`, `room_state`, and any other relevant `AllowedLogKey` fields. Conversation content is NEVER logged.
- **Wire format:** error codes appear in the Data Channel payload's `translation_status` field translation context only when `translation_status: "failed"` or `"low-confidence"` — the code itself is not put on the wire (it stays local per device). Cross-device error attribution is via UTC log timestamps + `(callId, utteranceId)` joins.
- **Cross-platform:** identical code strings across Android and iOS. CR agent verifies symmetry.

---

## 4. Adding a New Code

Process:

1. Propose ADR amendment in `architecture.md` (§10).
2. Simultaneous Kotlin + Swift PR introducing the code constant (typed enum entry — never magic string).
3. Update this file's registry table.
4. If the code maps to a new state in the failure-state taxonomy (architecture §7), update that too — and update the UX spec's failure-state inventory.

CR agent rejects:

- Magic-string error codes (must be from a typed source-of-truth constant set).
- Single-platform additions.
- Adding a code without updating this file.
