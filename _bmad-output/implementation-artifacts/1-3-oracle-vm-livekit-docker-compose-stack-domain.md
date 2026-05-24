# Story 1.3: Oracle VM + LiveKit Docker Compose Stack + Domain

Status: ready-for-dev

<!-- Created 2026-05-24 by feature/1-3-oracle-vm-scaffolding branch.
     Phase 0 (manual Oracle Cloud + DNS + SSH setup) is the gate; Phase 1
     (infra/ files written) + Phase 2 (deploy + verify) happen in follow-up
     dev sessions after Bania completes Phase 0. -->

## Story

As a solo developer,
I want a Docker Compose stack on an Oracle Cloud Ampere A1 VM running LiveKit + Caddy (TLS) + Redis + a Node.js auth-proxy,
so that the Android + iOS clients can establish authenticated, App-Check-gated WebRTC media + data-channel sessions over the public internet using the `xaeryx.com` domain.

## Acceptance Criteria

**Given** Story 1.4 (Firebase) is in `review` (App Check + Anonymous Auth available) and Bania has signed up for Oracle Cloud + has the `xaeryx.com` domain registered via Cloudflare,
**When** Bania completes the manual provisioning per [`docs/runbooks/oracle-vm-setup.md`](../../docs/runbooks/oracle-vm-setup.md) and a dev session implements the `infra/` stack + deploys it,
**Then:**

1. **AC-1 (Oracle VM provisioned + reachable):** An Always-Free Ampere A1 instance (Ubuntu 24.04 LTS ARM, 4 OCPU, 24 GB RAM, 200 GB block storage) exists in Bania's tenancy. SSH access works via key-pair (no password auth). Public IP is allocated + reserved (Reserved Public IP, NOT ephemeral — survives reboot). Docker Engine + Docker Compose v2 installed via the runbook's apt path. `docker compose version` reports v2.x.
2. **AC-2 (DNS + TLS):** `sfu.xaeryx.com` resolves to the VM's reserved public IP via Cloudflare DNS-only mode (no Cloudflare proxy — Cloudflare doesn't proxy WebRTC UDP traffic, would break media). Caddy auto-provisions a Let's Encrypt cert for the domain on first start; `curl https://sfu.xaeryx.com/healthz` returns `200 OK` with valid TLS chain.
3. **AC-3 (LiveKit reachable):** `livekit-server` is running on the VM in `livekit/livekit-server:latest` (multi-arch — ARM A1 supported). WebSocket signaling reachable at `wss://sfu.xaeryx.com`. A LiveKit client SDK test (e.g., `lk room list` via `livekit-cli` with an API key/secret pair) returns an empty room list (proves the server is alive + accepting authenticated calls).
4. **AC-4 (Auth-proxy implements the contract):** The Node.js Express auth-proxy at `infra/auth-proxy/` implements [`shared/auth-proxy-api.md`](../../shared/auth-proxy-api.md) — `POST /v1/token` mints a LiveKit JWT after verifying (a) the Firebase ID token via Firebase Admin SDK, (b) the App Check token via Admin SDK's `getAppCheck().verifyToken()`, (c) the requester is paired with `peerUid` per Firestore. Returns the JSON response shape documented in the contract. `GET /v1/healthz` returns `{"status":"ok","uptimeSeconds":<n>}`. Rate-limited per UID (10/min sliding window).
5. **AC-5 (E2E smoke):** A manual smoke test from Bania's desktop confirms the full chain: `curl -X POST https://sfu.xaeryx.com/v1/token` with a valid Firebase ID token + App Check token + paired peerUid → returns a non-empty `livekitJwt` field. The JWT, when fed to a LiveKit CLI client, successfully joins the deterministic `roomName` with `roomJoin: true` claims.
6. **AC-6 (Cost = $0/mo):** Total recurring cost is $0/mo (Oracle Always-Free) + ~$10/yr for the Cloudflare-registered domain (pre-existing, not new for this story). No paid GCP / AWS services. Egress is monitored manually via Oracle's billing dashboard; v1 audio-only traffic is <<10 TB/mo cap.

## Tasks / Subtasks

### Phase 0 — Bania's external setup (manual; runbook walks through)

**These tasks are NOT for an AI agent.** Bania completes them in browser + SSH per [`docs/runbooks/oracle-vm-setup.md`](../../docs/runbooks/oracle-vm-setup.md). The runbook is the source of truth; bullets here are a checklist mirror.

- [ ] **0.1** Sign up for Oracle Cloud (Always-Free tier — requires credit card for verification but no charges unless you exceed free limits). Pick a region with Ampere A1 availability (see runbook §1 for current best bets — `ap-singapore-1`, `eu-frankfurt-1`, and `us-phoenix-1` tend to have wider availability than `us-east-1` / `ap-tokyo-1` which are perpetually capacity-throttled).
- [ ] **0.2** Generate an SSH keypair (`ssh-keygen -t ed25519 -f ~/.ssh/oracle-translatorrep` — separate from any other project's keys) and add the public key to OCI when creating the instance.
- [ ] **0.3** Create an **Ampere A1 Compute** instance (NOT the AMD/Intel free tier — A1 is the 24 GB RAM ARM beast). Ubuntu 24.04 LTS ARM image. 4 OCPU / 24 GB RAM / 200 GB block storage. If "Out of capacity" — try off-peak hours, less-popular region, or the runbook's polling-loop workaround (§1.4).
- [ ] **0.4** Reserve the **Public IP** as a static "Reserved IP" (default is ephemeral — would change on reboot, would break DNS). One-time action via OCI Console.
- [ ] **0.5** Configure the VM's **Network Security List** to open inbound ports: `22/tcp` (SSH), `80/tcp` (Caddy ACME challenge), `443/tcp` (Caddy HTTPS), `7881/tcp` (LiveKit signaling), `7882/udp` (LiveKit ICE/TURN), `50000-60000/udp` (WebRTC media). Outbound: all (default).
- [ ] **0.6** In **Cloudflare DNS** for `xaeryx.com`: create an `A` record `sfu` → VM's reserved public IP. **Set proxy status to "DNS only" (gray cloud, NOT orange)** — Cloudflare does not proxy UDP traffic, and an orange-cloud proxy would silently break WebRTC media. Verify with `dig sfu.xaeryx.com` from your local machine — should return the VM's IP.
- [ ] **0.7** SSH into the VM (`ssh -i ~/.ssh/oracle-translatorrep ubuntu@<vm-ip>`). Follow runbook §3 to install: Docker Engine (apt), Docker Compose v2 plugin, basic firewall hardening (`ufw allow 22,80,443,7881/tcp; ufw allow 7882,50000:60000/udp; ufw enable`).
- [ ] **0.8** Verify the VM is ready for Phase 1: `docker compose version` reports v2.x; `dig sfu.xaeryx.com` from your laptop matches `curl -s ifconfig.me` run from the VM; basic `curl http://localhost` returns connection-refused (nothing listening yet — expected).

### Phase 1a — `infra/` config scaffolding (this PR, 2026-05-24)

Config-only scaffold landed in parallel with Bania's Phase 0 work. Auth-proxy TypeScript implementation deferred to Phase 1b (fresh Claude session) — that's the substantial piece (~200-400 lines of TS with Firebase Admin SDK + LiveKit Server SDK integration).

- [x] **1.1** Created `infra/` directory at repo root. Wrote `infra/docker-compose.yml` with 4 services. **Host networking on `livekit-server` + `redis`** (required for WebRTC ICE port-range; bridge networking would return container-internal IPs in ICE candidates which clients can't reach). `auth-proxy` block is pre-configured; service start fails with `./auth-proxy/Dockerfile not found` until Phase 1b lands (intentional gate; `deploy.sh` also pre-checks).
- [x] **1.2** Wrote `infra/livekit.yaml` — port 7880 signaling (Caddy-fronted), 7881 TCP fallback, 7882 UDP TURN, 50000-60000 UDP media, Redis at `127.0.0.1:6379` (host networking). `max_participants: 2` + `empty_timeout: 300` (matches Epic 7 leave-and-rejoin spec).
- [x] **1.3** Wrote `infra/Caddyfile` — single site `sfu.xaeryx.com`. `/v1/*` → `localhost:3000` (auth-proxy via host networking), else → `localhost:7880` (LiveKit). CORS preflight handler. Auto Let's Encrypt cert.
- [ ] **1.4** **Phase 1b (deferred)** — `infra/auth-proxy/` Node.js TypeScript implementation. Placeholder `infra/auth-proxy/README.md` documents the planned source tree.
- [x] **1.5** Wrote `infra/.env.example` documenting required env vars; `infra/.gitignore` excludes `.env`, `auth-proxy/node_modules`, `auth-proxy/dist`, `caddy_data/`, `caddy_config/`.
- [x] **1.6** Wrote `infra/deploy.sh` — `rsync` + `ssh ... docker compose up -d --build`. Supports `--pull` and `--logs`. Pre-checks `.env` exists + `auth-proxy/Dockerfile` exists. Exec bit set in git index.
- [x] **1.7** Wrote `infra/README.md` — operator runbook: prereqs, one-time setup, deploy command, verify steps, rollback, ops notes.

### Phase 1b — auth-proxy TypeScript implementation (fresh Claude session, after Phase 0 done)

- [ ] **1b.1** `infra/auth-proxy/package.json` + `tsconfig.json` + `vitest.config.ts`. Deps: `express`, `firebase-admin`, `livekit-server-sdk`, `zod`, `pino`. Dev deps: `typescript`, `@types/express`, `@types/node`, `tsx`, `vitest`.
- [ ] **1b.2** `src/index.ts` — Express bootstrap, port 3000.
- [ ] **1b.3** `src/middleware/auth.ts` — verify Firebase ID token + App Check token in parallel (Admin SDK).
- [ ] **1b.4** `src/middleware/paired.ts` — Firestore `/pairs/{pairId}` lookup; reject if requester not `memberA`/`memberB`.
- [ ] **1b.5** `src/middleware/rateLimit.ts` — per-UID sliding-window 10/min (in-memory Map).
- [ ] **1b.6** `src/routes/token.ts` (POST /v1/token) + `src/routes/healthz.ts` (GET /v1/healthz).
- [ ] **1b.7** `src/lib/livekit.ts` (AccessToken wrapper) + `src/lib/roomName.ts` (deterministic sha256-based room ID).
- [ ] **1b.8** `Dockerfile` — multi-stage TS build → distroless node22 runtime.
- [ ] **1b.9** `test/` — vitest unit tests (mock Admin SDK + LiveKit SDK at module boundary).

### Phase 2 — Deploy + verify (same session as Phase 1, or follow-up)

- [ ] **2.1** From your laptop: copy `infra/` to the VM via `infra/deploy.sh`. `docker compose up -d`. `docker compose ps` shows all 4 services Up + healthy.
- [ ] **2.2** Verify TLS: `curl https://sfu.xaeryx.com/v1/healthz` returns `200 OK` with valid Let's Encrypt cert (no `--insecure` flag needed). First call may take ~30s while Caddy provisions the cert.
- [ ] **2.3** Verify LiveKit reachable: install [livekit-cli](https://github.com/livekit/livekit-cli) on laptop, run `lk room list --url wss://sfu.xaeryx.com --api-key <key> --api-secret <secret>` → returns `[]` (empty list = server alive + authenticated).
- [ ] **2.4** Verify auth-proxy: from a debug Android build, sign in (Anonymous Auth) → get Firebase ID token + App Check token → `curl -X POST https://sfu.xaeryx.com/v1/token` with the body shape from `shared/auth-proxy-api.md` → returns 200 with `livekitJwt` field non-empty. (Bania may script this via a debug-only `LiveKitTokenTest.kt` similar to `FirebaseSmokeTest.kt` — explicitly out of scope for AC-5; AC-5 is the curl-based manual verification.)
- [ ] **2.5** Record output of all four verifications in Dev Agent Record → Debug Log References.

### Phase 3 — Optional CI deploy automation (deferred)

- [ ] **3.1** Update `.github/workflows/infra-ci.yml` (currently a stub) — on push to main with paths under `infra/**`, validate `docker-compose.yml` (`docker compose config`), `yamllint livekit.yaml`, and (optionally) auto-deploy via SSH if a `INFRA_DEPLOY_KEY` secret is set + the commit is tagged `infra-v*`. Defer until Phase 1 + 2 stable.

## Dev Notes

### Why this story matters now

Stories 2.1 (auth-proxy + JWT mint) and 2.2 (paired-home call button + CallSession scaffolding) directly call into the auth-proxy. The whole Epic 2 (audio calling) cascading-blocks on Story 1.3 — there's no way to make a LiveKit call without a reachable LiveKit Server + a working JWT minter. This story unblocks Epic 2 entirely.

### Region selection guidance (runbook §1.1 cross-ref)

Oracle Ampere A1 "Out of capacity" is the single most common Phase 0 blocker. Cross-referencing community reports as of 2026-05 (subject to change):

| Region | A1 availability (2026-05) | Latency to Indonesia | Latency to US East | Notes |
|---|---|---|---|---|
| `ap-singapore-1` | Generally available | ~30ms | ~250ms | **First-choice for this project.** Closest to target user base. |
| `ap-tokyo-1` | Frequently throttled | ~80ms | ~150ms | Try at off-peak (~2am UTC). |
| `eu-frankfurt-1` | Generally available | ~180ms | ~85ms | Second choice if SG throttled. |
| `us-phoenix-1` | Generally available | ~230ms | ~60ms | Acceptable fallback; worst case for girlfriend's latency. |
| `us-east-1` (Ashburn) | Perpetually throttled | ~250ms | ~10ms | Avoid. |
| `ap-mumbai-1` | Generally available | ~70ms | ~200ms | Reasonable alternative to SG. |

### Why Caddy not nginx

- Auto Let's Encrypt cert provisioning + auto-renew with zero config. Nginx requires `certbot` + cron renew + reload — extra moving parts on a solo-dev VM.
- Caddy's JSON-API config is more debuggable than nginx's text config when something breaks at 11pm.
- Single binary, ARM-native.

### Why a separate auth-proxy (not LiveKit-direct token signing on client)

- LiveKit JWT requires the API secret to sign. Embedding the secret in the mobile app is a leak vector — anyone disassembling the APK could mint arbitrary tokens.
- App Check verification has to happen server-side (the App Check token is opaque to clients; Firebase Admin SDK verifies it).
- Pairing check requires Firestore read with privileged credentials.
- Centralizing all three (sign token, verify App Check, check pairing) in one auth-proxy is the architecture per ADR-C1 + C2.

### Why `sfu.xaeryx.com` for both auth-proxy + LiveKit (single subdomain)

- Originally the architecture suggested `sfu.xaeryx.com` for LiveKit + `auth.xaeryx.com` for auth-proxy — two subdomains.
- This story consolidates to one subdomain (`sfu.xaeryx.com`) with Caddy path-routing: `/v1/*` → auth-proxy, everything else → LiveKit.
- Reasons: one DNS record to manage, one TLS cert to provision, fewer moving parts. Trade-off: the auth-proxy is reachable via the same host as LiveKit (mild information disclosure — but the endpoints are well-known anyway).
- If we ever need to split (e.g., to put auth-proxy behind a CDN or scale it independently), it's a config change in Caddyfile + a new DNS record — no client-side changes (clients hit `sfu.xaeryx.com/v1/token` either way).

### Previous story intelligence

**Story 1.4 (Firebase):**
- Anonymous Auth + Firestore + App Check active on `translatorrep-8d773`. Auth-proxy will:
  - Verify Firebase ID tokens via `admin.auth().verifyIdToken(token)`.
  - Verify App Check tokens via `admin.appCheck().verifyToken(token)`.
  - Read `/pairs/{pairId}` to check pairing — requires service account with Firestore read permission (default if the Admin SDK service account is project Owner / Editor).
- The Firebase service account JSON needs to be generated (Firebase console → Project settings → Service accounts → Generate new private key) and placed on the VM as an env var (base64'd into `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64`). Phase 1.4 + Phase 0 runbook covers this.

**Story 1.6 (CI/CD):**
- `infra-ci.yml` is currently a stub. Story 1.6c (currently `backlog`) will flesh it out to actually validate `infra/**` changes. This story creates `infra/` for the first time, so 1.6c becomes meaningful right after this lands.

### Library + SDK references

- [LiveKit Server Docker docs](https://docs.livekit.io/realtime/self-hosting/deployment/) — official deployment patterns including ARM.
- [LiveKit Server SDK for Node](https://docs.livekit.io/server/server-apis/server-sdks/node-js/) — `AccessToken` for minting JWTs.
- [Firebase Admin SDK — verifyIdToken](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Firebase App Check — verifyToken (server)](https://firebase.google.com/docs/app-check/custom-resource-backend#verify-token)
- [Caddy v2 auto-HTTPS](https://caddyserver.com/docs/automatic-https)
- [Oracle Cloud Always-Free Ampere A1](https://www.oracle.com/cloud/free/) — official free-tier docs (note: marketing pages aren't fully accurate; runbook §1 has actual usable instructions).
- [Cloudflare DNS-only mode for non-HTTP traffic](https://developers.cloudflare.com/dns/manage-dns-records/reference/proxied-dns-records/) — why orange-cloud breaks WebRTC.

### Source-tree placement

```
infra/                                # Created by this story (Phase 1.1)
├── docker-compose.yml                # 4 services: livekit-server, redis, caddy, auth-proxy
├── livekit.yaml                      # LiveKit Server config
├── Caddyfile                         # TLS + reverse proxy config
├── .env.example                      # Documented required env vars (real .env gitignored)
├── .gitignore                        # Excludes .env + node_modules + Firebase service account JSON
├── deploy.sh                         # SSH rsync + docker compose up
├── README.md                         # Operator runbook (deploy + rollback + troubleshoot)
└── auth-proxy/                       # Node.js + Express + TypeScript service
    ├── package.json
    ├── tsconfig.json
    ├── vitest.config.ts
    ├── Dockerfile                    # Multi-stage TS build → distroless node22 runtime
    ├── src/
    │   ├── index.ts                  # Express bootstrap
    │   ├── middleware/
    │   │   ├── auth.ts               # Firebase + App Check verify
    │   │   ├── paired.ts             # Firestore pairing check
    │   │   └── rateLimit.ts          # Per-UID sliding window
    │   ├── routes/
    │   │   ├── token.ts              # POST /v1/token
    │   │   └── healthz.ts            # GET /v1/healthz
    │   └── lib/
    │       ├── livekit.ts            # AccessToken minting wrapper
    │       └── roomName.ts           # Deterministic room-name hash
    └── test/
        ├── token.spec.ts
        ├── paired.spec.ts
        └── rateLimit.spec.ts

docs/runbooks/
└── oracle-vm-setup.md                # NEW (this PR) — Phase 0 walkthrough
```

### Testing standards

- **Auth-proxy unit tests:** vitest. Mock `firebase-admin` + Firestore + LiveKit SDK at the module boundary (don't hit network). Cover: happy path (200 with mint), each error path (400/401/403/429/500). Aim for ≥80% branch coverage on `src/` excluding `index.ts` bootstrap.
- **No instrumented tests** against the deployed VM in this story — integration testing is the manual smoke (AC-5).
- **CI for `infra/auth-proxy/`:** `infra-ci.yml` (currently stub) — Story 1.6c flesh-out runs `npm ci && npm test && npm run build && docker build .` on PRs that touch `infra/**`. Out of scope for this story.

### Project Structure Notes

- `infra/` is a top-level peer to `android/`, `ios/`, `shared/`, `docs/`, `firebase/`, `_bmad-output/`. Matches architecture §"Repo Shape — Monorepo, Per-Stack Roots".
- The auth-proxy is the ONLY Node.js/TypeScript code in the project. Per-stack roots are sacrosanct (architecture §11 + project-context.md §3) — no auth-proxy code under `android/` or vice versa.
- `infra/auth-proxy/package-lock.json` MUST be committed (pin transitive deps; reproducible builds).

### References

- [architecture.md §C "Backend & Auth"](../planning-artifacts/architecture.md#c-backend--auth) — ADR-C1 (auth-proxy on Oracle) + C2 (App Check on auth-proxy)
- [architecture.md §"Oracle VM Deployment"](../planning-artifacts/architecture.md#oracle-vm-deployment-infra--adr-a1--adr-c1) — `infra/` source-tree spec
- [shared/auth-proxy-api.md](../../shared/auth-proxy-api.md) — HTTP API contract (canonical)
- [docs/runbooks/oracle-vm-setup.md](../../docs/runbooks/oracle-vm-setup.md) — Phase 0 walkthrough
- [Story 1.4](./1-4-firebase-init-firestore-rules-baseline-app-check-providers.md) — provides Firebase Auth + App Check infrastructure this story consumes

## Dev Agent Record

### Agent Model Used

_(filled in at implementation time — Phase 1 + Phase 2 sessions)_

### Debug Log References

_(filled in at implementation time — Oracle Cloud region picked + click-through time, VM provisioning duration, capacity-issue retries if any, deploy.sh output, smoke-test curl output, any unexpected LiveKit/Caddy/Firebase Admin SDK errors + resolutions)_

### Completion Notes List

_(filled in at implementation time — VM region + IP, LiveKit API key prefix (first 4 chars only, never full secret), monthly free-tier usage observed, anything surprising about the Oracle / Caddy / LiveKit stack)_

### File List

_(filled in at implementation time)_

### Change Log

- 2026-05-24 — Story 1.3 file created (status `ready-for-dev`). Scaffolding PR commits the story file + Phase 0 runbook (`docs/runbooks/oracle-vm-setup.md`) so Bania can start Oracle Cloud signup in parallel. Phase 1 (`infra/` Docker Compose stack + auth-proxy TypeScript) + Phase 2 (deploy + verify) land in follow-up dev sessions after Phase 0 completes. Decision: single subdomain `sfu.xaeryx.com` for both LiveKit + auth-proxy (Caddy path-routes `/v1/*` to auth-proxy, else LiveKit) — simpler than the originally-architected `sfu.` + `auth.` two-subdomain split.
- 2026-05-24 (later) — **Phase 1a landed in parallel with Bania's Phase 0 work** (Bania doing Oracle signup async). Config-only scaffold: `infra/docker-compose.yml` (4 services with host networking on livekit+redis for WebRTC ICE), `livekit.yaml` (max_participants:2 + empty_timeout:300 per Epic 7 spec), `Caddyfile` (sfu.xaeryx.com single site + path routing + CORS), `.env.example` + `.gitignore`, `deploy.sh` (rsync + ssh + docker compose, with pre-deploy checks), `README.md` (operator runbook), `auth-proxy/README.md` (Phase 1b placeholder). Phase 1 was SPLIT into 1a (config files; this PR) + 1b (auth-proxy TypeScript; fresh session). Status stays `ready-for-dev` until Phase 1b + Phase 2 land.
