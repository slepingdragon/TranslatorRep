# TranslatorRep auth-proxy — multi-stage build.
# Owning story: 1-3-oracle-vm-livekit-docker-compose-stack-domain (Phase 1b).
#
# Build stage: Node 22 (LTS), install deps + TypeScript build.
# Runtime stage: distroless/nodejs22-debian12 (no shell, no apt, minimal CVE surface).

# ───────────── Build stage ─────────────
FROM node:22-bookworm-slim AS build

WORKDIR /app

# Copy manifests first (Docker layer cache: rebuild deps only when package.json changes)
COPY package.json package-lock.json* ./

# Install ALL deps (incl. devDependencies — needed for tsc)
RUN npm ci --include=dev

# Copy source + TS config + build
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Prune devDependencies — runtime only needs production deps + dist/
RUN npm prune --omit=dev

# ───────────── Runtime stage ─────────────
# Distroless Node 22 — no shell, no apt, minimal attack surface.
# https://github.com/GoogleContainerTools/distroless
FROM gcr.io/distroless/nodejs22-debian12 AS runtime

WORKDIR /app

# Copy only what's needed at runtime: built JS + production deps.
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/package.json ./package.json

ENV NODE_ENV=production

# Port matches docker-compose.yml's `expose: 3000`.
EXPOSE 3000

# Distroless image has no shell — use direct node invocation.
# CMD form is exec-array (not shell-form) so signals (SIGTERM from docker stop) reach Node directly.
CMD ["dist/index.js"]
