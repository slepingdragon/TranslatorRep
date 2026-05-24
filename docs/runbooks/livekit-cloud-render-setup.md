# LiveKit Cloud + Render Setup (auth-proxy deploy)

> **Story 2.1 deploy layer.** The SFU is **LiveKit Cloud** (the Oracle self-hosting path was dropped — see memory/CONTEXT). The **auth-proxy** (`infra/auth-proxy/`) runs as a Docker web service on **Render**. This runbook is the one-time setup; most of it is account/secret work only Bania can do. The code + the Render Blueprint (`render.yaml`) are already committed.

---

## 0. The security & privacy model (why this is the private choice)

| Concern | How it's handled |
|---|---|
| **Call media content** | **End-to-end encrypted** via LiveKit Insertable Streams (Epic 5): keys are derived client-side from per-call X25519 ECDH (identity keys from Story 1.12) and **never sent to any server**. LiveKit Cloud relays media it **cannot decrypt**. Until Epic 5 lands, media is still DTLS-SRTP (encrypted in transit). |
| **Media at rest** | **No recording.** LiveKit Cloud does not record unless egress is configured — we never configure it (and with E2EE it couldn't read the media anyway). |
| **Who can get a call token** | Only a genuine, signed-in app instance: the auth-proxy verifies a **Firebase ID token + App Check** token before minting, and only for the caller's **own pair's room**. |
| **Token blast radius** | JWTs are **60-second TTL**, scoped to a single deterministic room (`roomJoin` + that `room` only) — a leaked token expires fast and grants nothing beyond that pair's room. |
| **Secrets** | LiveKit API key/secret + Firebase service-account JSON live **only in Render's secret env store** (`sync: false` in `render.yaml`) — never in git, never in logs (SafeLog allowlist + `pino` redaction). |
| **Data at rest (Firestore)** | Metadata only — pairing codes, pair docs, public keys. **No conversation content, no display names beyond what each user sets.** |
| **Transport** | Render terminates **TLS (HTTPS)**; the client talks `wss://` to LiveKit Cloud. No plaintext anywhere. |
| **Region / data locality** | LiveKit Cloud project + Render service both in **Singapore** — closest to Indonesia, keeps signaling/token traffic in-region. |

Net: the servers see encrypted media they can't read, short-lived room-scoped tokens, and metadata only. The privacy guarantee is the **client-side E2EE** (Epic 5) + the **gated, minimal, short-lived** token issuance (already built).

---

## Part A — Accounts + secrets (Bania, one-time)

### A1. LiveKit Cloud (the SFU)

1. Sign up / log in at **https://cloud.livekit.io**.
2. **Create a project** — name it `translatorrep`. **Region: Singapore** (`ap-southeast`).
3. **Do NOT enable** recording/egress, or any analytics that inspects media. (Default is off.)
4. From **Project → Settings → Keys**, **create an API key** → copy the **API Key** and **API Secret** (shown once — store in your password manager).
5. Copy the project **WebSocket URL** — it looks like `wss://translatorrep-xxxx.livekit.cloud` (Project → Settings, or the "Connect" panel).

You now have three values: **WS URL**, **API Key**, **API Secret**.

### A2. Firebase service-account (for the auth-proxy)

You already generated this for the app, but the auth-proxy needs it base64'd as one line:

- Firebase console → Project settings → **Service accounts → Generate new private key** → save the JSON.
- Base64 it (one line, no wrapping):
  - **PowerShell:** `[Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\service-account.json"))`
- Keep that string for A3. (Project ID `translatorrep-8d773` is already set in `render.yaml`.)

### A3. Render (host the auth-proxy)

1. Sign up / log in at **https://render.com** (connect your GitHub).
2. **New → Blueprint** → select the `TranslatorRep` repo. Render reads **`render.yaml`** and proposes the `translatorrep-auth-proxy` web service.
3. When prompted, paste the four secret env vars (they're `sync: false`, so Render asks for them and stores them encrypted):
   - `LIVEKIT_WS_URL` = the LiveKit Cloud WS URL from A1.5
   - `LIVEKIT_API_KEY` = from A1.4
   - `LIVEKIT_API_SECRET` = from A1.4
   - `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64` = from A2
4. **Apply / Create** → Render builds the Docker image and deploys. (Plan is `starter` = always-on; change to `free` in the dashboard if you'd rather — works, but cold-starts after idle.)
5. Copy the service URL: `https://translatorrep-auth-proxy.onrender.com`.

### A4. App Check (so the auth-proxy's verification has something to check)

- Firebase console → **App Check** → ensure the Android app is registered (Play Integrity for release; the debug provider token for debug builds — register it if Firestore/token calls get rejected in a debug build).

---

## Part B — Verify (Bania)

```
curl https://translatorrep-auth-proxy.onrender.com/v1/healthz
```

Expect HTTP 200 with a small JSON status body. (`/v1/token` can't be tested by hand — it needs a real Firebase ID + App Check token; the app exercises it in Story 2.3.)

---

## Part C — Hand back to dev (then Story 2.3 wires the client)

Give me these two URLs and I'll wire them into the Android client config + `LiveKitRoomManager.connect`:

- **LiveKit WS URL** → `wss://translatorrep-xxxx.livekit.cloud`
- **auth-proxy base URL** → `https://translatorrep-auth-proxy.onrender.com`

Then Story 2.3 (place a call) does: client gets a Firebase ID + App Check token → `POST {auth-proxy}/v1/token` → receives the short-lived room-scoped JWT → `room.connect(wsUrl, jwt)`. Real audio call.

---

## Notes / superseded files

- The Oracle self-hosting artifacts — `infra/docker-compose.yml`, `infra/livekit.yaml`, `infra/Caddyfile`, `infra/deploy.sh`, `infra/scripts/oracle-capacity-loop.sh`, `docs/runbooks/oracle-vm-setup.md` — are **superseded** by this LiveKit-Cloud-+-Render path. They're left in the repo (CI-validated, harmless) but unused; safe to delete in a future cleanup. `infra/auth-proxy/` is the only piece that deploys.
- `render.yaml` lives at the repo root (Render's expected Blueprint location); it scopes the build to `infra/auth-proxy/` via `rootDir`.
- Rotating a secret: change it in Render → Environment → redeploy. To rotate the LiveKit key, create a new key in LiveKit Cloud, update Render, delete the old key.
