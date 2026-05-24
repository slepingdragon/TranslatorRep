# Oracle VM + Cloudflare DNS Setup (Story 1.3 Phase 0)

> **Authority:** Phase 0 walkthrough for [Story 1.3](../../_bmad-output/implementation-artifacts/1-3-oracle-vm-livekit-docker-compose-stack-domain.md). You complete this in browser + SSH; a follow-up Claude session writes the `infra/` Docker Compose stack + deploys it (Phase 1 + 2).
>
> **Estimated time:** ~2 hours if Oracle Ampere A1 capacity is available immediately. ~1-5 days if you hit "Out of capacity" errors and have to retry (extremely common — read §1.4 carefully).
>
> **Prereq:** Cloudflare account owning `xaeryx.com` (Brady already has this).

---

## §1. Oracle Cloud account + Ampere A1 VM

### §1.1 Sign up for Oracle Cloud

1. Go to https://signup.cloud.oracle.com/ → **Sign Up Free**.
2. Email, country, name, address — required.
3. **Credit card required for identity verification.** No charges unless you exceed Always-Free limits, but they hold $1 temporarily to verify the card.
4. **Home region selection** — THIS IS PERMANENT. Pick a region with reliable Ampere A1 availability:

| Region | A1 availability (2026-05) | Latency to Indonesia | Latency to US East | Picks |
|---|---|---|---|---|
| `ap-singapore-1` | ✅ Generally available | ~30ms | ~250ms | **First-choice — closest to girlfriend** |
| `ap-mumbai-1` | ✅ Generally available | ~70ms | ~200ms | Solid backup for Indonesia |
| `eu-frankfurt-1` | ✅ Generally available | ~180ms | ~85ms | Balanced for both |
| `ap-tokyo-1` | ⚠️ Frequently throttled | ~80ms | ~150ms | Try off-peak only |
| `us-phoenix-1` | ✅ Generally available | ~230ms | ~60ms | If you prefer your-side latency |
| `us-east-1` Ashburn | ❌ Perpetually throttled | ~250ms | ~10ms | **Don't pick this** |

**Recommendation: `ap-singapore-1`** — girlfriend latency wins, and your audio calls will work fine at ~250ms (humans tolerate up to ~400ms for voice).

5. Click through verification — account activation takes ~5-20 min via email.

### §1.2 Create SSH keypair (local — your laptop)

Before creating the VM, generate the SSH key it'll use:

**PowerShell:**
```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\oracle-translatorrep" -C "translatorrep-oracle-vm"
```

When prompted for passphrase, **set one** (don't leave blank — this key has root-equivalent access to your VM).

Two files are now in `~/.ssh/`:
- `oracle-translatorrep` — private key. **Never share or commit.** Permissions auto-set on Windows.
- `oracle-translatorrep.pub` — public key. This is what you'll paste into Oracle's console.

### §1.3 Provision the Ampere A1 instance

1. Oracle Cloud Console → top-left ☰ menu → **Compute → Instances**.
2. **Create instance**.
3. **Name:** `translatorrep-sfu`.
4. **Image and shape:**
   - **Image:** Click **Change image** → pick **Canonical Ubuntu 24.04** (LTS, ARM-compatible).
   - **Shape:** Click **Change shape** → **Ampere** category → **VM.Standard.A1.Flex** → set **OCPUs = 4**, **Memory = 24 GB** (this is the maximum Always-Free; smaller is wasteful since you're paying nothing).
5. **Networking:**
   - Default VCN + subnet is fine (Oracle creates one automatically if you don't have one).
   - **Public IPv4 address:** **Assign public IPv4 address** = YES.
6. **Add SSH keys:**
   - Select **Paste public keys**.
   - Paste the contents of `~/.ssh/oracle-translatorrep.pub` (open it in Notepad, copy all).
7. **Boot volume:** default (50 GB) is fine; you can grow to 200 GB Always-Free if needed later.
8. **Create**. Wait ~2 min for the instance to provision.

### §1.4 If you hit "Out of capacity" (common — read this)

Oracle's Ampere A1 is the most popular Always-Free shape, and it's frequently capacity-constrained. You'll see errors like `Out of host capacity` or `InternalError` on instance creation. This is normal; don't panic.

**Workarounds (try in order):**

1. **Retry at off-peak.** Try 2-6am UTC (when fewer EU/US devs are signing up). Often works within 30 min.
2. **Switch region** to the second-choice from §1.1's table. Region IS permanent for the tenancy, BUT you can create a new "child tenancy" by signing up with a different email. Or just bail to a different region from the start.
3. **Polling-loop workaround** — write a small bash script that retries `oci compute instance launch` every 5-10 min via the Oracle CLI. Public gists like [hitrov/oci-arm-host-capacity](https://github.com/hitrov/oci-arm-host-capacity) exist for this; reads your config + retries automatically. Run on your laptop while you sleep.
4. **Give up after ~3 days** and switch region. Some regions are persistently saturated for months.

**Don't try to "upgrade" to a paid account** to bypass capacity — same shape, same capacity pool. Doesn't help.

### §1.5 Reserve the Public IP (one-time, do immediately after launch)

The default public IP is **ephemeral** — it changes if you stop/start the VM. You need a Reserved IP so DNS doesn't break on reboot.

1. Console → ☰ menu → **Networking → IP Management → Reserved IPs**.
2. **Reserve public IP** → name `translatorrep-sfu-ip` → Create.
3. Back to ☰ menu → **Compute → Instances** → click your instance → **Attached VNICs** → click the VNIC → **IPv4 Addresses** → click ⋮ on the primary IP → **Edit** → switch from **Ephemeral** to **Reserved Public IP** → pick `translatorrep-sfu-ip` → save.

Now the VM has a permanent public IP. Note it down — you'll need it for DNS (§2.1).

### §1.6 Network Security List — open required ports

LiveKit + WebRTC need UDP ports open. Default OCI security list only allows port 22 (SSH).

1. Console → ☰ → **Networking → Virtual Cloud Networks** → click your VCN → **Security Lists** → click the default security list.
2. **Add Ingress Rules** — add these 5 rules:

| Source | IP Protocol | Source Port | Destination Port | Purpose |
|---|---|---|---|---|
| `0.0.0.0/0` | TCP | All | `80` | Caddy ACME challenge (Let's Encrypt) |
| `0.0.0.0/0` | TCP | All | `443` | Caddy HTTPS (TLS) |
| `0.0.0.0/0` | TCP | All | `7881` | LiveKit signaling (HTTP) |
| `0.0.0.0/0` | UDP | All | `7882` | LiveKit ICE/TURN |
| `0.0.0.0/0` | UDP | All | `50000-60000` | WebRTC media (large port range — this is normal for SFUs) |

Leave the existing `22/tcp` rule alone — that's your SSH access.

**Save**. Rules take effect within ~30s.

---

## §2. Cloudflare DNS

### §2.1 Create the `sfu.xaeryx.com` A record

1. Cloudflare dashboard → `xaeryx.com` zone → **DNS** → **Records**.
2. **Add record**:
   - **Type:** A
   - **Name:** `sfu` (Cloudflare auto-appends `.xaeryx.com`)
   - **IPv4 address:** the Reserved Public IP from §1.5
   - **Proxy status:** ⚠️ **DNS only (gray cloud)** — NOT Proxied (orange). Cloudflare does not proxy UDP traffic and an orange-cloud setting would silently break WebRTC media. This is the single most common Cloudflare-related mistake.
   - **TTL:** Auto.
3. **Save**.

### §2.2 Verify DNS propagation

From your laptop (any terminal):

```powershell
nslookup sfu.xaeryx.com
```

Should return your VM's reserved public IP within ~1 min. If it returns Cloudflare proxy IPs (like 104.x.x.x), you accidentally enabled Proxied — go back and switch to DNS-only.

---

## §3. SSH in + install Docker

### §3.1 First SSH connection

```powershell
ssh -i "$env:USERPROFILE\.ssh\oracle-translatorrep" ubuntu@<your-vm-ip>
```

Type `yes` to accept the host key on first connection. Enter your passphrase. You're now in.

(Optional: add an SSH config alias so you don't have to retype the path every time. Add to `~/.ssh/config`:
```
Host translatorrep
    HostName <your-vm-ip>
    User ubuntu
    IdentityFile ~/.ssh/oracle-translatorrep
```
Then `ssh translatorrep` just works.)

### §3.2 Install Docker Engine + Compose v2 (run on the VM)

Copy-paste these commands one by one. Standard Docker apt install (https://docs.docker.com/engine/install/ubuntu/):

```bash
# Update + install apt prereqs
sudo apt update
sudo apt install -y ca-certificates curl gnupg

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Add Docker repo
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
$(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add ubuntu user to docker group so you don't need sudo for docker commands
sudo usermod -aG docker ubuntu
```

**Log out and back in** for the group membership to take effect:
```bash
exit
```
Then `ssh translatorrep` again.

**Verify:**
```bash
docker --version          # Docker version 27.x.x or later
docker compose version    # Docker Compose version v2.x.x
docker run hello-world    # Should pull + run successfully
```

### §3.3 Firewall hardening (UFW)

Oracle's security list (§1.6) is the primary firewall, but `ufw` on the VM is belt-and-suspenders:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 7881/tcp
sudo ufw allow 7882/udp
sudo ufw allow 50000:60000/udp
sudo ufw enable        # Type `y` when prompted
sudo ufw status        # Verify all rules listed
```

⚠️ **Before running `sudo ufw enable`, confirm rule 1 (`22/tcp`) is present.** UFW will lock you out of SSH if you enable it without an SSH allow rule. The order above is safe.

---

## §4. Verification checklist

Before declaring Phase 0 done, verify ALL of these:

- [ ] **SSH works:** `ssh translatorrep` connects without password prompt (just passphrase for the key).
- [ ] **DNS resolves:** `nslookup sfu.xaeryx.com` from laptop returns the VM's reserved public IP (NOT a Cloudflare proxy IP).
- [ ] **Docker runs:** `docker run hello-world` succeeds on the VM.
- [ ] **Docker Compose v2:** `docker compose version` reports `v2.x.x` (NOT `docker-compose` — the standalone v1 binary).
- [ ] **Ports open via Oracle security list:** From your laptop, `curl -v telnet://<vm-ip>:80` should connect (no listener yet, so it'll fail at the protocol level, but the TCP handshake should complete).
- [ ] **UFW enabled:** `sudo ufw status` on VM shows all 6 rules + `Status: active`.
- [ ] **Reserved IP confirmed:** Oracle Console → Networking → IP Management → Reserved IPs shows `translatorrep-sfu-ip` as **Assigned**.

---

## §5. What happens after Phase 0

When you've completed §1-§4, ping me in a fresh chat:

> Oracle Phase 0 done. VM IP is `<ip>`, region is `<region>`, SSH alias is `translatorrep`. Ready for Story 1.3 Phase 1.

A fresh Claude session will then:

1. **Write `infra/`** — `docker-compose.yml`, `livekit.yaml`, `Caddyfile`, `auth-proxy/` (Node.js + Express + TypeScript implementing the API contract).
2. **You generate the Firebase Admin SDK service account** in Firebase Console → Project settings → Service accounts → Generate new private key. Download the JSON, base64 it, store in the VM's `infra/.env` (gitignored).
3. **Deploy** — `rsync` `infra/` to the VM, `docker compose up -d`, verify all 4 services healthy.
4. **Smoke test** — curl the deployed auth-proxy with a real Firebase ID token + App Check token, verify it mints a LiveKit JWT.
5. **Story 1.3 → done.** Epic 2 (audio calling) is now fully unblocked.

---

## §6. Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `Out of host capacity` on instance creation | Oracle Ampere A1 region throttled | §1.4 workarounds |
| Public IP changes after VM reboot | Forgot to reserve the IP (§1.5) | Reserve it; update Cloudflare DNS |
| `nslookup sfu.xaeryx.com` returns 104.x.x.x | Cloudflare proxy enabled (orange cloud) | Switch to DNS-only (gray cloud); WebRTC will break with proxy on |
| SSH locked out after `ufw enable` | UFW rules didn't include 22/tcp | Stop the VM → mount the boot volume on another instance → edit `/etc/ufw/user.rules` to allow 22. Pain. Don't skip step in §3.3. |
| `docker: permission denied` | User not in `docker` group | `sudo usermod -aG docker ubuntu` → log out + back in |
| Caddy fails to provision Let's Encrypt cert (Phase 1) | Port 80 not actually reachable from internet | Re-verify Oracle security list (§1.6) AND `ufw status` (§3.3); BOTH need 80/tcp open |
| LiveKit media connects but no audio | Port range 50000-60000/udp blocked | Re-verify both Oracle security list + ufw allow that range |
| `Always Free` tier billed | Account inactive 7+ days during first month | Log in weekly during first month. After that, status is permanent. |

---

## §7. Cost monitoring

Oracle Always-Free is genuinely free, BUT there are limits:

- **Compute:** 4 OCPU + 24 GB RAM Ampere A1 = max free tier. You're using all of it.
- **Block storage:** 200 GB free across all volumes.
- **Egress:** 10 TB/mo free. Audio-only LiveKit at 32 kbps × 2 users × 60 min/day × 30 days = ~14 GB/mo. You're nowhere close to the cap until you have hundreds of daily users.

Monitor at: Oracle Console → ☰ → **Billing → Cost Analysis**. Set up a budget alert at $1/mo (you should never see any charges).

---

## §8. References

- [Oracle Always-Free official docs](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [LiveKit self-hosting guide](https://docs.livekit.io/realtime/self-hosting/deployment/)
- [Caddy server installation](https://caddyserver.com/docs/install)
- [hitrov/oci-arm-host-capacity](https://github.com/hitrov/oci-arm-host-capacity) — capacity polling script
- [Story 1.3](../../_bmad-output/implementation-artifacts/1-3-oracle-vm-livekit-docker-compose-stack-domain.md) — the story this runbook enables
- [shared/auth-proxy-api.md](../../shared/auth-proxy-api.md) — the API contract Phase 1 implements
- [architecture.md §C "Backend & Auth"](../../_bmad-output/planning-artifacts/architecture.md#c-backend--auth) — ADR-C1 + C2 reasoning
