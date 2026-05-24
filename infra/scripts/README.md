# `infra/scripts/`

Operational scripts for the Oracle VM that hosts the LiveKit + Caddy + Redis + auth-proxy stack (architecture §"C. Backend & Auth", Story 1.3).

---

## `oracle-capacity-loop.sh`

**What:** Polls Oracle Cloud every 5 minutes and auto-provisions an Always-Free `VM.Standard.A1.Flex` Ampere VM when capacity opens up.

**Why:** Oracle A1 free-tier capacity in `ap-singapore-1` (and most other regions) is famously throttled — Console returns `Out of host capacity in availability domain AD-1` on most create attempts. The workarounds in the community:

1. Retry at off-peak hours (Asia early morning)
2. Polling-loop workaround — THIS SCRIPT
3. Pay for capacity (defeats the free-tier point)
4. Switch regions (home region is permanent post-signup; would need new tenancy)

This script is option 2.

### One-time setup

1. **Install OCI CLI** (Python required):
   ```powershell
   pip install oci-cli
   oci --version
   ```

2. **Generate Oracle API key + configure CLI:**
   ```powershell
   oci setup config
   ```
   Accept defaults. Then upload the public key to Oracle Console:
   - Open `~/.oci/oci_api_key_public.pem` in Notepad, copy contents
   - Console → profile icon (top-right) → **My profile** → **API keys** → **Add API key** → paste → Add
   - Sanity-check: `oci iam region list` should print JSON

3. **Generate SSH key for the VM** (if not already done):
   ```powershell
   New-Item -ItemType Directory -Path "$env:USERPROFILE\.ssh" -Force
   ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\oracle-translatorrep" -C "translatorrep-oracle-vm"
   ```
   Set a passphrase. Two files appear in `~/.ssh/`:
   - `oracle-translatorrep` (private — never share)
   - `oracle-translatorrep.pub` (public — script reads this)

4. **Collect 3 OCIDs from Oracle Console:**

   | OCID | Where to find it |
   |---|---|
   | **Compartment OCID** | Top-right profile icon → **Tenancy: \<name\>** → copy OCID at top |
   | **Availability Domain name** | Compute → Instances → Create instance → AD dropdown shows exact name (e.g., `xkrA:AP-SINGAPORE-1-AD-1`) |
   | **Subnet OCID** | Networking → Virtual Cloud Networks → click the default VCN → click the subnet → copy OCID |

   (Image OCID is auto-looked-up by the script.)

5. **Fill placeholders** at the top of `oracle-capacity-loop.sh`.

### Run

```bash
# From git-bash on Windows
chmod +x oracle-capacity-loop.sh
./oracle-capacity-loop.sh
```

Leave the git-bash window open. The script writes to `~/oracle-capacity-loop.log` so you can `tail -f` from another terminal.

### On success

The script:
1. Logs success to `~/oracle-capacity-loop.log` with the new instance's full JSON
2. Triggers Windows TTS: **"Oracle A1 capacity success. Check your terminal."**
3. Beeps for 30 seconds straight

You'll hear it from another room. Exits cleanly so you know the VM is provisioned.

### On unexpected error (not capacity)

The script:
1. Logs the error
2. Triggers TTS: **"Oracle capacity bot encountered an unexpected error. Please check."**
3. Exits with code 1

Usually means: bad OCID, missing CLI auth, expired API key, or SSH key file gone. Check the log, fix, re-run.

### Tunables

Inside the script:

- `OCPUS` / `MEMORY_GB` — drop both (e.g., 1 OCPU / 6 GB) if 4/24 capacity stays blocked for days. Smaller shapes sometimes provision when max-Always-Free doesn't. You can resize the A1 later without recreating.
- `POLL_INTERVAL_SECONDS` — Oracle's rate-limit guidance is generous; 5 min is conservative. Don't go below 60 seconds — risks rate-limit (`TooManyRequests`).
- `DISPLAY_NAME` — change if running multiple loops with different shapes simultaneously.

### Stopping

`Ctrl+C` in the terminal. Log is preserved.

---

## What this directory will hold once Story 1.3 lands

Story 1.3's main scope is the LiveKit + Caddy + Redis + auth-proxy Docker Compose stack. When it lands, this directory grows:

- `deploy.sh` — push compose file + restart services on the VM
- `bootstrap.sh` — initial VM provisioning (Docker install, firewall config, certbot)
- `backup.sh` — periodic state snapshots (Redis dump, Caddy certs)
- `health.sh` — uptime check + auto-restart trigger
- `oracle-capacity-loop.sh` — this file (kept for future re-provisioning needs)
