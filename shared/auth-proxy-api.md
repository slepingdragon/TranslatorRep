# Auth-Proxy HTTP API Contract

> **Authority:** This file is the canonical contract for the Node.js auth-proxy service that lives on the Oracle VM alongside `livekit-server`, `redis`, and `caddy` (Architecture ADR-C1 / ADR-C2). Story 2.1 implements this contract; Stories 5.x / 6.x / 7.x consume tokens minted by it.
>
> **Updated:** 2026-05-22 (Story 1.7, satisfies Gap I.11)
> **Versioning:** Path-versioned (`/v1/token`). Bump only on backward-incompatible request/response shape changes.

---

## Service Location

- **Production base URL:** `https://auth.xaeryx.com/v1/` (subject to ADR-C1 final decision — may be a path under `sfu.xaeryx.com` instead). Update this file on Story 2.1 deploy.
- **TLS:** Auto Let's Encrypt via Caddy (no manual cert rotation).
- **Runtime:** Node.js (Express + TypeScript) on Oracle Ampere A1 VM (Ubuntu 24.04 LTS ARM).
- **Co-located with:** `livekit-server`, `redis`, `caddy` in the same `docker-compose.yml`.

---

## Endpoint: `POST /v1/token`

Mint a short-lived LiveKit JWT for the requesting (attested) device.

### Request

**Headers:**

| Header | Required | Notes |
|---|---|---|
| `Content-Type` | yes | `application/json` |
| `Authorization` | no | Reserved for future; not used in v1 (App Check is in body) |

**Body (JSON):**

```json
{
  "firebaseIdToken": "<Firebase Auth ID token string>",
  "appCheckToken": "<Firebase App Check token string>",
  "callType": "audio",
  "peerUid": "<Partner's Firebase Auth UID>"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `firebaseIdToken` | string | yes | From `FirebaseAuth.currentUser.getIdToken()` on Android / `Auth.auth().currentUser?.getIDToken()` on iOS. Verified server-side via Firebase Admin SDK. |
| `appCheckToken` | string | yes (may be empty) | From Firebase App Check (DeviceCheck iOS / Play Integrity Android). **The field must be present but may be an empty string** when the client can't obtain a token (emulators have no Play Integrity; debug builds without a registered debug token). When `APP_CHECK_ENFORCED=true` it is verified via Admin SDK `getAppCheck().verifyToken(token)` against JWKS at `https://firebaseappcheck.googleapis.com/v1/jwks` — empty/invalid → 401 (`ERR_APP_CHECK_INVALID`). When `APP_CHECK_ENFORCED=false` (dev/testing) the field is accepted without verification. Note: emptiness is **not** rejected at the request-parse stage, so the enforcement toggle actually governs behavior. |
| `callType` | string enum | yes | `"audio"` \| `"video"`. Determines LiveKit room track-publishing permissions and embeds in JWT metadata claim for use by clients. |
| `peerUid` | string | yes | The other Paired User's Firebase Auth UID. Used to derive a deterministic `roomName`. Server verifies the requester is actually paired with `peerUid` (Firestore `/pairs/{pairId}` lookup with requester as memberA or memberB). |

### Response

**200 OK:**

```json
{
  "livekitJwt": "<JWT string>",
  "roomName": "call-<hash>",
  "expiresAt": "2026-05-22T14:00:00.000Z",
  "livekitWsUrl": "wss://sfu.xaeryx.com"
}
```

| Field | Type | Notes |
|---|---|---|
| `livekitJwt` | string | Short-lived JWT (TTL ≤ 60s). Includes claims: `sub` (Firebase UID), `roomJoin: true`, `room: <roomName>`, `metadata: '{"callType": "audio\|video"}'`. Minted via `livekit-server-sdk`. |
| `roomName` | string | Deterministic name: `call-<base32(sha256(sorted_uids_joined_with_dash))>` truncated to 32 chars. Both partners hashing the same sorted-uid set yields the same `roomName` regardless of who calls first. |
| `expiresAt` | string (ISO 8601) | Wall-clock expiration time of the JWT. |
| `livekitWsUrl` | string | LiveKit server WebSocket URL. Hardcoded `wss://sfu.xaeryx.com` in v1; included in response for v2 flexibility. |

**Error responses:**

| Status | Body | When |
|---|---|---|
| 400 | `{ "error": "ERR_INVALID_REQUEST", "message": "<detail>" }` | Body parse failure, missing required field, invalid `callType` enum |
| 401 | `{ "error": "ERR_FIREBASE_TOKEN_INVALID" }` | Firebase ID token verification failed |
| 401 | `{ "error": "ERR_APP_CHECK_INVALID" }` | App Check token verification failed |
| 403 | `{ "error": "ERR_NOT_PAIRED" }` | Requester is not paired with `peerUid` per Firestore |
| 429 | `{ "error": "ERR_RATE_LIMITED", "retryAfterMs": 5000 }` | Per-UID rate limit hit (10 tokens/min sliding window — protects against runaway client bugs) |
| 500 | `{ "error": "ERR_INTERNAL" }` | LiveKit SDK failure or other server-side error |

All error responses include the `Content-Type: application/json` header.

---

## Retry & Idempotency

- Token mint is **idempotent** for the same `(firebaseIdToken, peerUid, callType)` within a 60-second window — the server may return the same JWT (cached) or a new one with identical claims; clients MUST tolerate both.
- Client retry strategy: exponential backoff with full jitter, base 1s, cap 16s, max 3 retries on 5xx; no retry on 4xx (except 429 honoring `retryAfterMs`).
- App Check token refresh handled by Firebase SDK on the client; if `ERR_APP_CHECK_INVALID` returns, the client should force-refresh and retry once.

---

## Logging Contract

The auth-proxy logs every request with these fields ONLY (privacy-safe, no PII, no conversation content):

- Request: `request_id`, `requester_uid_hash` (SHA-256 of UID), `call_type`, `outcome` (`granted` / `<error_code>`), `latency_ms`
- Never logged: raw UIDs, tokens, peer UIDs, request body content beyond `callType`.

Logs are local on the Oracle VM (Docker logs, rotated by `caddy` defaults). No external log shipping in v1.

---

## Health Check

`GET /v1/healthz` returns `200 OK` with body `{"status":"ok","uptimeSeconds":<integer>}`. No auth required. Used for monitoring (manual / future tooling).

---

## Out of Scope (v2 candidates)

- Per-UID quota beyond rate limit.
- Token revocation (no use case in v1 — tokens are 60s TTL).
- Webhook notifications.
- gRPC variant.
