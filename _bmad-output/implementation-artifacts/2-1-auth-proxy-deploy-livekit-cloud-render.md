# Story 2.1: Auth-Proxy Deploy — LiveKit Cloud + Render (Android backend)

Status: in-progress

<!-- Created 2026-05-24. RETITLED from "auth-proxy-oracle-vm-..." — Oracle is OUT; the SFU is
     LiveKit Cloud and the auth-proxy is hosted on Render. The auth-proxy CODE (App Check
     verification + LiveKit JWT mint) was built in Story 1.3 Phase 1b; this story is the DEPLOY
     + client wiring. Phase 1 (deploy config, this PR) is autonomous; Phase 2 needs Bania's
     accounts; Phase 3 (client JWT fetch) overlaps Story 2.3. -->

## Story

As Bania,
I want the already-built auth-proxy deployed on Render against a LiveKit Cloud SFU, with security/privacy-optimal config,
so that the Android client can obtain short-lived, room-scoped LiveKit JWTs (after Firebase Auth + App Check) and place real calls.

## Acceptance Criteria

**Phase 1 — deploy config (this PR, autonomous):**

1. **AC-1 (Render Blueprint):** `render.yaml` (repo root) defines the `translatorrep-auth-proxy` web service — Docker runtime, `rootDir: infra/auth-proxy`, Singapore region, `/v1/healthz` health check, `autoDeploy: false`, with the four secret env vars (`LIVEKIT_WS_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`) as `sync: false` + non-secret `NODE_ENV`/`FIREBASE_PROJECT_ID`.
2. **AC-2 (host-agnostic, no code change needed):** the auth-proxy reads Render's injected `$PORT` (`app.listen(env.PORT)`), binds all interfaces, and handles SIGTERM — Render-ready. `LIVEKIT_WS_URL` is now required (no dead `sfu.xaeryx.com` default).
3. **AC-3 (runbook + security model):** `docs/runbooks/livekit-cloud-render-setup.md` documents Bania's account/secret steps + the security/privacy model (E2EE relays, no recording, gated/short-lived/room-scoped tokens, secrets only in Render, metadata-only Firestore, Singapore region).
4. **AC-4 (validated):** auth-proxy still typechecks + 23 tests + builds; `render.yaml` is valid YAML.

**Phase 2 — deploy (Bania, per the runbook):** create the LiveKit Cloud project (Singapore, no recording) + API key; deploy the Render Blueprint with the four secrets; `curl /v1/healthz` → 200; hand back the LiveKit WS URL + Render auth-proxy URL.

**Phase 3 — client wiring (overlaps Story 2.3):** Android config gets the two URLs; `LiveKitRoomManager.connect` fetches a Firebase ID + App Check token → `POST {auth-proxy}/v1/token` → `room.connect(wsUrl, jwt)`.

**Done criteria:** flips to `done` after Phase 2 (`/v1/healthz` green on Render) + Phase 3 (a real call connects in Story 2.3). Phase 1 lands now.

## Tasks / Subtasks

### Phase 1 (this PR)

- [x] **1.1** `render.yaml` Blueprint (Docker web service; Singapore; health check; secret env via `sync: false`; `autoDeploy: false`).
- [x] **1.2** `docs/runbooks/livekit-cloud-render-setup.md` — account steps + security/privacy model + verify + hand-back.
- [x] **1.3** `infra/.env.example` updated for LiveKit Cloud (add `LIVEKIT_WS_URL`, `FIREBASE_PROJECT_ID`; keys come from LiveKit Cloud). `env.ts`: `LIVEKIT_WS_URL` required (dropped dead Oracle default). `test/setup.ts` URL → `*.livekit.cloud`.
- [x] **1.4** Validate: auth-proxy typecheck + 23 tests + build green; `render.yaml` valid YAML.

### Phase 2 (Bania — runbook) / Phase 3 (Story 2.3)

- [ ] **2.x** Bania: LiveKit Cloud project + key; Render deploy + secrets; verify `/v1/healthz`.
- [ ] **3.x** Client: token fetch + `room.connect` (wired in Story 2.3, replacing `LiveKitRoomManager.connect`'s scaffold).

## Dev Notes

### Why Render + LiveKit Cloud (and what makes it the private choice)

Oracle self-hosting is dropped. **LiveKit Cloud** is the managed SFU; the **auth-proxy** (the only thing we self-run) is a tiny Docker web service on **Render**. The privacy guarantee isn't "trust the SFU" — it's:
- **Client-side E2EE** (Epic 5, Insertable Streams keyed off the Story-1.12 X25519 identity via per-call ECDH): LiveKit Cloud relays media it can't decrypt; **no recording** is ever configured.
- **Gated, minimal, short-lived tokens:** the auth-proxy verifies Firebase ID + App Check before minting a **60 s**, **single-room-scoped** JWT (built in 1.3 1b — `lib/livekit.ts`).
- **Secrets only in Render's encrypted store** (`sync: false`), never git/logs; **metadata-only Firestore**; **Singapore** region for data locality.

Full table in the runbook §0.

### No auth-proxy code change required

The service was written host-agnostic + env-driven (Story 1.3 1b): `app.listen(env.PORT)` (Render injects `$PORT`), SIGTERM graceful shutdown, zod-validated env. The only edits: make `LIVEKIT_WS_URL` required (drop the dead `wss://sfu.xaeryx.com` Oracle default) + refresh comments. The distroless multi-stage `Dockerfile` is what Render builds.

### `render.yaml` choices

- `runtime: docker` + `rootDir: infra/auth-proxy` — builds only the proxy in this monorepo.
- `region: singapore` — match LiveKit Cloud + closest to Indonesia.
- `plan: starter` — always-on (no cold start mid-call-setup); `free` works for testing (spins down). Security identical.
- `autoDeploy: false` — deploys are manual (review before shipping a token-minting service).
- `healthCheckPath: /v1/healthz` — Render gates traffic on it.

### Superseded Oracle artifacts

`infra/docker-compose.yml`, `infra/livekit.yaml`, `infra/Caddyfile`, `infra/deploy.sh`, `infra/scripts/oracle-capacity-loop.sh`, `docs/runbooks/oracle-vm-setup.md` are now unused (self-hosted path). Left in place (CI-validated, harmless); safe to delete in a future cleanup. Noted in the runbook.

### References

- [docs/runbooks/livekit-cloud-render-setup.md](../../docs/runbooks/livekit-cloud-render-setup.md) — the setup.
- [Render Blueprint spec](https://render.com/docs/blueprint-spec) — `render.yaml`.
- [LiveKit Cloud](https://docs.livekit.io/home/cloud/) + [Insertable Streams E2EE](https://docs.livekit.io/home/client/tracks/encryption/) (Epic 5).
- [infra/auth-proxy/](../../infra/auth-proxy/) — the service (Story 1.3 1b); `lib/livekit.ts` (60 s room-scoped JWT).
- [Story 2.3](../planning-artifacts/epics.md) — wires the client token fetch + `room.connect`.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24)

### Debug Log References

- auth-proxy after env change: `npm run typecheck` clean, `npm test` 23/23, `npm run build` clean; `render.yaml` yamllint (relaxed, 120) exit 0.

### Completion Notes List

- Deploy reframed Oracle → LiveKit Cloud + Render. No auth-proxy logic change (host-agnostic); only made `LIVEKIT_WS_URL` required + refreshed env docs.
- Security/privacy model documented (E2EE relays, no recording, gated short-lived room-scoped tokens, secrets in Render only, Singapore region).
- Phase 2 (accounts/deploy) is Bania's per the runbook; Phase 3 (client wiring) is Story 2.3.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/2-1-auth-proxy-deploy-livekit-cloud-render.md` — this file
- `render.yaml` — Render Blueprint (auth-proxy)
- `docs/runbooks/livekit-cloud-render-setup.md` — setup + security/privacy model

**Modified:**
- `infra/auth-proxy/src/env.ts` — `LIVEKIT_WS_URL` required (dropped Oracle default); LiveKit-Cloud comments
- `infra/auth-proxy/test/setup.ts` — stub WS URL → `*.livekit.cloud`
- `infra/.env.example` — LiveKit Cloud context (+ `LIVEKIT_WS_URL`, `FIREBASE_PROJECT_ID`)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 2-1 → in-progress; last_updated bump
- `docs/project-context.md` — Epic-2 deploy note

### Change Log

- 2026-05-24 — Story 2.1 Phase 1 (deploy config). Render Blueprint + LiveKit-Cloud-+-Render setup runbook (security/privacy model) + env docs; auth-proxy made host-required for `LIVEKIT_WS_URL`. No proxy logic change. auth-proxy 23 tests + build green; render.yaml valid. Phase 2 (Bania's accounts/deploy) + Phase 3 (client wiring, Story 2.3) remain. Status `in-progress`.
