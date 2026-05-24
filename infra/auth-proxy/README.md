# auth-proxy — Story 1.3 Phase 1b (NOT YET IMPLEMENTED)

This directory will hold a Node.js + Express + TypeScript service that implements the contract in [`shared/auth-proxy-api.md`](../../shared/auth-proxy-api.md).

**Status as of 2026-05-24:** placeholder only. The `infra/docker-compose.yml` references this directory as a build context, but `deploy.sh` will refuse to deploy until the implementation lands (it checks for `Dockerfile` presence).

## What this service does

Per the API contract:

- **`POST /v1/token`** — mints a short-lived LiveKit JWT after:
  1. Verifying the Firebase Auth ID token (via Firebase Admin SDK `verifyIdToken`).
  2. Verifying the Firebase App Check token (via Admin SDK `getAppCheck().verifyToken()`).
  3. Confirming the requesting user is paired with the `peerUid` (Firestore `/pairs/{pairId}` lookup).
  4. Computing a deterministic room name from the sorted UID pair.
  5. Minting the JWT with LiveKit Server SDK + 60s TTL.
- **`GET /v1/healthz`** — returns `{"status":"ok","uptimeSeconds":<n>}`. Used by Docker healthcheck + Caddy upstream check.
- Per-UID rate limiting: 10 tokens/min sliding window.
- Structured logging (pino) with `request_id`, `requester_uid_hash`, `call_type`, `outcome`, `latency_ms`.

## When this lands

Fresh Claude session, prompt: "Do Story 1.3 Phase 1b — auth-proxy TypeScript implementation."

Implementation scope (per Story 1.3 Phase 1.4):

```
infra/auth-proxy/
├── package.json
├── tsconfig.json
├── vitest.config.ts
├── Dockerfile                      # Multi-stage TS build → distroless node22 runtime
├── src/
│   ├── index.ts                    # Express bootstrap, port 3000
│   ├── middleware/
│   │   ├── auth.ts                 # Verify Firebase ID + App Check tokens
│   │   ├── paired.ts               # Firestore /pairs/{pairId} membership check
│   │   └── rateLimit.ts            # Per-UID sliding window
│   ├── routes/
│   │   ├── token.ts                # POST /v1/token
│   │   └── healthz.ts              # GET /v1/healthz
│   └── lib/
│       ├── livekit.ts              # AccessToken minting wrapper
│       └── roomName.ts             # Deterministic sha256-based room ID
└── test/
    ├── token.spec.ts               # Happy + error paths
    ├── paired.spec.ts
    └── rateLimit.spec.ts
```

## Why deferred

Phase 1a (this PR) scaffolds the Docker Compose + Caddy + LiveKit config (deterministic config files, low risk). Auth-proxy is ~200-400 lines of TypeScript with subtle Firebase Admin SDK + LiveKit Server SDK integration. Deferring to a fresh Claude context window reduces risk of subtle bugs.

## Why this README exists in a not-yet-implemented dir

Without ANY file in `infra/auth-proxy/`, git wouldn't track the directory at all. This README is the placeholder that keeps the dir present + documents the planned structure for the next implementer.
