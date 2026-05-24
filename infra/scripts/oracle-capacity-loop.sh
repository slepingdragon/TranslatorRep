#!/usr/bin/env bash
# Oracle A1.Flex capacity polling loop.
#
# Purpose: Oracle's Always-Free A1.Flex (Ampere ARM) capacity in `ap-singapore-1`
# (and most other regions) is famously throttled — the Console returns
# "Out of host capacity" on instance create. This script polls every 5 min
# and provisions the VM the moment capacity opens up.
#
# Setup + usage: see `./README.md` (infra/scripts/README.md).
# Story trace: Story 1.3 prep workaround (2026-05-24).

set -uo pipefail

# ─── FILL THESE IN (from Oracle Console) ─────────────────────────────────
COMPARTMENT_OCID="FILL_ME_compartment_ocid"
AVAILABILITY_DOMAIN="FILL_ME_AD_name"           # e.g. xkrA:AP-SINGAPORE-1-AD-1
SUBNET_OCID="FILL_ME_subnet_ocid"
SSH_KEY_FILE="$HOME/.ssh/oracle-translatorrep.pub"

# ─── KNOBS (defaults are fine for our 2-user LiveKit stack) ──────────────
SHAPE="VM.Standard.A1.Flex"
OCPUS=4                                          # max Always-Free A1 = 4 OCPU / 24 GB
MEMORY_GB=24                                     # drop both if capacity stays blocked
DISPLAY_NAME="translatorrep-livekit"
POLL_INTERVAL_SECONDS=300                        # 5 min — tweak as desired
LOG_FILE="$HOME/oracle-capacity-loop.log"

# ─── PREFLIGHT ───────────────────────────────────────────────────────────
for placeholder in "$COMPARTMENT_OCID" "$AVAILABILITY_DOMAIN" "$SUBNET_OCID"; do
    if [[ "$placeholder" == FILL_ME_* ]]; then
        echo "ERROR: A FILL_ME_ placeholder is still in the script. Edit the top of this file." >&2
        exit 1
    fi
done

if ! command -v oci >/dev/null 2>&1; then
    echo "ERROR: 'oci' CLI not found. Install with: pip install oci-cli" >&2
    exit 1
fi

if [[ ! -f "$SSH_KEY_FILE" ]]; then
    echo "ERROR: SSH public key not found at $SSH_KEY_FILE" >&2
    echo "       Generate with: ssh-keygen -t ed25519 -f \"$HOME/.ssh/oracle-translatorrep\"" >&2
    exit 1
fi

# Auto-look-up latest Ubuntu 22.04 ARM image OCID for this region
echo "Looking up latest Ubuntu 22.04 ARM image for $SHAPE..."
IMAGE_OCID=$(oci compute image list \
    --compartment-id "$COMPARTMENT_OCID" \
    --operating-system "Canonical Ubuntu" \
    --operating-system-version "22.04" \
    --shape "$SHAPE" \
    --sort-by TIMECREATED --sort-order DESC --limit 1 \
    --query 'data[0]."id"' --raw-output 2>/dev/null) || {
    echo "ERROR: Image lookup failed. Sanity-check OCI CLI auth with:" >&2
    echo "       oci iam region list" >&2
    exit 1
}
echo "  Image OCID: $IMAGE_OCID"

# ─── LOG START ───────────────────────────────────────────────────────────
{
    echo "═══════════════════════════════════════════════════════════════"
    echo "Started: $(date)"
    echo "  Shape:  $SHAPE ($OCPUS OCPU, ${MEMORY_GB}GB)"
    echo "  AD:     $AVAILABILITY_DOMAIN"
    echo "  Poll:   every ${POLL_INTERVAL_SECONDS}s"
    echo "  Log:    $LOG_FILE"
    echo "═══════════════════════════════════════════════════════════════"
} | tee -a "$LOG_FILE"

# ─── MAIN LOOP ───────────────────────────────────────────────────────────
attempt=0
while true; do
    attempt=$((attempt + 1))
    ts="$(date '+%Y-%m-%d %H:%M:%S')"
    echo "[$ts] Attempt #$attempt..." | tee -a "$LOG_FILE"

    result=$(oci compute instance launch \
        --compartment-id "$COMPARTMENT_OCID" \
        --availability-domain "$AVAILABILITY_DOMAIN" \
        --shape "$SHAPE" \
        --shape-config "{\"ocpus\":$OCPUS,\"memoryInGBs\":$MEMORY_GB}" \
        --image-id "$IMAGE_OCID" \
        --subnet-id "$SUBNET_OCID" \
        --display-name "$DISPLAY_NAME" \
        --ssh-authorized-keys-file "$SSH_KEY_FILE" \
        --wait-for-state RUNNING \
        2>&1)
    exit_code=$?

    if [[ $exit_code -eq 0 ]]; then
        {
            echo "═══════════════════════════════════════════════════════════════"
            echo "✅ SUCCESS at $ts (attempt #$attempt)"
            echo "═══════════════════════════════════════════════════════════════"
            echo "$result"
        } | tee -a "$LOG_FILE"

        # Loud notification — Windows TTS + 30 seconds of beeps so you hear it from another room
        powershell -Command \
            "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('Oracle A1 capacity success. Check your terminal.')" \
            2>/dev/null || true
        for _ in {1..30}; do echo -en "\a"; sleep 1; done
        exit 0
    fi

    if echo "$result" | grep -qiE "Out of host capacity|OutOfCapacity|TooManyRequests"; then
        echo "  → Out of capacity. Sleeping ${POLL_INTERVAL_SECONDS}s..." | tee -a "$LOG_FILE"
        sleep "$POLL_INTERVAL_SECONDS"
    else
        {
            echo "═══════════════════════════════════════════════════════════════"
            echo "❌ UNEXPECTED ERROR at $ts (not capacity — config or auth issue)"
            echo "═══════════════════════════════════════════════════════════════"
            echo "$result"
        } | tee -a "$LOG_FILE"
        powershell -Command \
            "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('Oracle capacity bot encountered an unexpected error. Please check.')" \
            2>/dev/null || true
        exit 1
    fi
done
