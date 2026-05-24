# TranslatorRep `infra/` — Oracle VM Docker Compose stack

> **Owning story:** [1-3-oracle-vm-livekit-docker-compose-stack-domain](../_bmad-output/implementation-artifacts/1-3-oracle-vm-livekit-docker-compose-stack-domain.md).
>
> **Architecture:** [ADR-A1 (Oracle ARM A1)](../_bmad-output/planning-artifacts/architecture.md#a1) + [ADR-C1 (auth-proxy on Oracle)](../_bmad-output/planning-artifacts/architecture.md#c1).

---

## What lives here

Four services in `docker-compose.yml`, deployed to a single Oracle Cloud Ampere A1 VM:

| Service | Image | Purpose | External port |
|---|---|---|---|
| `livekit-server` | `livekit/livekit-server:v1.7` | WebRTC SFU (signaling + media) | 7881/tcp, 7882/udp, 50000-60000/udp |
| `redis` | `redis:7-alpine` | LiveKit's state backend | Internal only |
| `caddy` | `caddy:2-alpine` | TLS termination + reverse proxy (Let's Encrypt auto-cert) | 80/tcp, 443/tcp |
| `auth-proxy` | (built from `./auth-proxy/`) | LiveKit JWT minting + Firebase Admin SDK App Check verification | Internal only (Caddy fronts at `/v1/*`) |

**As of 2026-05-24 (this scaffolding PR):** the config files (docker-compose.yml, livekit.yaml, Caddyfile, .env.example, deploy.sh, this README) are committed. The **`auth-proxy/` TypeScript implementation** is deferred to Story 1.3 Phase 1b (fresh Claude session). `deploy.sh` will refuse to run until Phase 1b lands.

---

## One-time setup (per machine where you'll run `deploy.sh`)

### Prereqs

- ✅ Story 1.3 Phase 0 complete (Oracle VM provisioned + Cloudflare DNS + Docker installed per [docs/runbooks/oracle-vm-setup.md](../docs/runbooks/oracle-vm-setup.md)).
- ✅ SSH alias `translatorrep` configured in `~/.ssh/config` (runbook §3.1).
- ✅ `rsync` installed locally (Git Bash on Windows has it; native Windows doesn't).
- ✅ Phase 1b landed (`auth-proxy/Dockerfile` exists) — fresh Claude session does this.

### Step 1 — Generate LiveKit API keys

Two strings, both alphanumeric, ≥16 chars each. Store in your password manager.

Easiest:
```bash
docker run --rm livekit/livekit-server generate-keys
```

Outputs something like:
```
API Key:    APIxxxxxxxxxxxx
API Secret: yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
```

### Step 2 — Generate Firebase service account JSON

1. https://console.firebase.google.com/project/translatorrep-8d773/settings/serviceaccounts/adminsdk
2. **Generate new private key** → downloads a `.json` file. **Treat as a secret** — anyone with this can impersonate any Firebase user.
3. Base64-encode it (one line, no wrapping):
   - **PowerShell:** `[Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\downloaded.json")) | Set-Clipboard`
   - **Bash:** `base64 -w 0 downloaded.json | xclip -selection clipboard`
4. Save the base64 string for the next step.

### Step 3 — Populate `infra/.env`

```bash
cp .env.example .env
# Edit .env, paste in the three values.
```

`.env` is gitignored (per `.gitignore` in this dir).

### Step 4 — Verify auth-proxy is built

Phase 1b deferred. Skip this step until that lands. `deploy.sh` will check for you.

---

## Deploy

```bash
cd infra/
./deploy.sh                # rsync + docker compose up -d --build
./deploy.sh --pull         # also pull latest images
./deploy.sh --logs         # follow logs after deploy
```

First deploy takes ~3-5 min (cold pull of images on the VM + Caddy ACME cert provisioning). Subsequent deploys are ~30s.

---

## Verify (post-deploy)

From your laptop:

```bash
# Health check via Caddy → auth-proxy (proves TLS + reverse proxy + auth-proxy alive)
curl https://sfu.xaeryx.com/v1/healthz
# Expected: 200 OK with {"status":"ok","uptimeSeconds":<n>}

# LiveKit signaling reachable (proves WebSocket upgrade works)
# Requires livekit-cli installed (https://github.com/livekit/livekit-cli)
lk room list \
  --url wss://sfu.xaeryx.com \
  --api-key <LIVEKIT_API_KEY from .env> \
  --api-secret <LIVEKIT_API_SECRET from .env>
# Expected: [] (empty room list = server alive + auth working)
```

---

## Rollback

```bash
ssh translatorrep "cd /home/ubuntu/translatorrep && git pull && docker compose up -d --build"
```

(Currently a no-op since the VM doesn't pull from git directly — `deploy.sh` rsyncs from your laptop. If you want git-pull-based deploys, change `deploy.sh` to `ssh translatorrep "git -C /home/ubuntu/translatorrep pull && docker compose up -d --build"` after setting up a deploy key on the VM.)

For emergency stop:
```bash
ssh translatorrep "cd /home/ubuntu/translatorrep && docker compose down"
```

---

## Operations notes

### Logs

```bash
ssh translatorrep "cd /home/ubuntu/translatorrep && docker compose logs -f --tail=100"
ssh translatorrep "cd /home/ubuntu/translatorrep && docker compose logs livekit-server --tail=200"
```

### Update LiveKit

Bump the `image:` tag in `docker-compose.yml` (e.g., `v1.7` → `v1.8`), then `./deploy.sh --pull`.

### Renew TLS cert

Caddy auto-renews 30 days before expiry. No manual action needed. Verify with:
```bash
ssh translatorrep "cd /home/ubuntu/translatorrep && docker compose logs caddy | grep certificate"
```

### Free-tier resource check

Oracle Always-Free limits — verify monthly:
```bash
# CPU + RAM usage
ssh translatorrep "htop -d 5"

# Egress bytes (TB cap is 10/mo; you should never get close)
ssh translatorrep "vnstat -m"
```

---

## Known limitations (v1 baseline)

- Single-node — no horizontal scaling. Fine for 2-user audience.
- No automated backups (LiveKit + Redis state is ephemeral; only cert data in `caddy_data` matters and that's auto-renewed).
- No external log shipping — `docker logs` only (Story 1.3 architecture decision).
- Rate limiting at LiveKit is implicit (Oracle VM CPU cap); auth-proxy has its own per-UID rate limit (Phase 1b code).
- No metrics dashboard (defer Plan B trigger per architecture ADR-A1).

---

## References

- [LiveKit self-hosting docs](https://docs.livekit.io/realtime/self-hosting/deployment/)
- [Caddy v2 reverse proxy](https://caddyserver.com/docs/quick-starts/reverse-proxy)
- [Oracle Cloud Always-Free shapes](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [shared/auth-proxy-api.md](../shared/auth-proxy-api.md) — the HTTP API contract auth-proxy implements
- [docs/runbooks/oracle-vm-setup.md](../docs/runbooks/oracle-vm-setup.md) — Bania's Phase 0 walkthrough
