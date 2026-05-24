#!/usr/bin/env bash
# infra/deploy.sh — deploy Docker Compose stack to the Oracle VM.
# Owning story: 1-3-oracle-vm-livekit-docker-compose-stack-domain (Phase 2 task 2.1).
#
# Usage:
#   ./deploy.sh              # rsync + docker compose up -d --build
#   ./deploy.sh --pull       # also `docker compose pull` first (image updates)
#   ./deploy.sh --logs       # tail logs after deploy
#
# Prereqs:
#   - Story 1.3 Phase 0 done per docs/runbooks/oracle-vm-setup.md.
#   - ~/.ssh/config has alias `translatorrep` for the Oracle VM (per runbook §3.1).
#   - infra/.env exists locally with real values (gitignored).

set -euo pipefail

SSH_HOST="${SSH_HOST:-translatorrep}"
REMOTE_DIR="${REMOTE_DIR:-/home/ubuntu/translatorrep}"
LOCAL_DIR="$(dirname "${BASH_SOURCE[0]}")"

# Sanity check: must have .env locally.
if [[ ! -f "$LOCAL_DIR/.env" ]]; then
    echo "::error:: $LOCAL_DIR/.env missing. Copy .env.example and fill in real values." >&2
    exit 1
fi

# Sanity check: auth-proxy directory must contain a Dockerfile (Phase 1b).
if [[ ! -f "$LOCAL_DIR/auth-proxy/Dockerfile" ]]; then
    echo "::error:: $LOCAL_DIR/auth-proxy/Dockerfile missing." >&2
    echo "Phase 1b (auth-proxy TypeScript implementation) hasn't landed yet." >&2
    echo "See _bmad-output/implementation-artifacts/1-3-...md Phase 1.4 tasks." >&2
    exit 1
fi

echo "🚀 Deploying TranslatorRep infra to $SSH_HOST..."

# Sync infra/ to the VM. Excludes per .gitignore + node_modules + caddy_data
# (volumes-backed, lives on VM only).
rsync -avz --delete \
    --exclude='.git' \
    --exclude='node_modules' \
    --exclude='dist' \
    --exclude='caddy_data' \
    --exclude='caddy_config' \
    "$LOCAL_DIR/" "$SSH_HOST:$REMOTE_DIR/"

# Run docker compose on the VM.
COMPOSE_CMD="cd $REMOTE_DIR && docker compose"

if [[ "${1:-}" == "--pull" ]]; then
    COMPOSE_CMD+=" pull && docker compose up -d --build --remove-orphans"
    shift
else
    COMPOSE_CMD+=" up -d --build --remove-orphans"
fi

echo "▶️  Running on VM: $COMPOSE_CMD"
ssh "$SSH_HOST" "$COMPOSE_CMD"

echo "✅ Deploy complete. Services:"
ssh "$SSH_HOST" "cd $REMOTE_DIR && docker compose ps"

if [[ "${1:-}" == "--logs" ]]; then
    echo "📜 Tailing logs (Ctrl+C to detach)..."
    ssh "$SSH_HOST" "cd $REMOTE_DIR && docker compose logs -f --tail=100"
fi
