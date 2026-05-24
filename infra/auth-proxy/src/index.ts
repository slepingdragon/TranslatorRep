import express from "express";
import { getEnv } from "./env.js";
import { initFirebaseAdmin } from "./firebaseAdmin.js";
import { logger } from "./lib/logger.js";
import { errorHandler } from "./middleware/errorHandler.js";
import { healthzHandler } from "./routes/healthz.js";
import { tokenChain } from "./routes/token.js";

/**
 * TranslatorRep auth-proxy — Express bootstrap.
 * Owning story: 1-3-oracle-vm-livekit-docker-compose-stack-domain (Phase 1b).
 *
 * Routes (path-versioned per shared/auth-proxy-api.md):
 *   POST /v1/token    → tokenChain (validate + auth + rate-limit + paired + mint)
 *   GET  /v1/healthz  → healthzHandler (uptime + status)
 *
 * Reverse-proxied by Caddy from `sfu.xaeryx.com/v1/*` (infra/Caddyfile).
 * No direct internet exposure — Caddy fronts.
 */

const env = getEnv();

// Initialize Firebase Admin SDK at boot — fail fast if service account JSON
// is malformed (better than first-request crash).
initFirebaseAdmin();

const app = express();

// Body parser — JSON, with a small max payload (tokens are ≤8KB; pad to 32KB).
app.use(express.json({ limit: "32kb" }));

// Routes.
app.get("/v1/healthz", healthzHandler);
app.post("/v1/token", ...tokenChain);

// Error handler — MUST be registered last.
app.use(errorHandler);

// 404 catch-all for unknown paths.
app.use((_req, res) => {
    res.status(404).json({ error: "ERR_NOT_FOUND" });
});

const server = app.listen(env.PORT, () => {
    logger.info({ port: env.PORT, node_env: env.NODE_ENV }, "auth-proxy listening");
});

// Graceful shutdown — drain in-flight requests on SIGTERM (Docker stop sends SIGTERM).
function shutdown(signal: string): void {
    logger.info({ signal }, "shutdown signal received; closing server");
    server.close(() => {
        logger.info({}, "server closed; exiting");
        process.exit(0);
    });
    // Hard-timeout: if shutdown takes > 10s, force exit so Docker doesn't SIGKILL us mid-write.
    setTimeout(() => {
        logger.warn({}, "graceful shutdown timed out; force-exiting");
        process.exit(1);
    }, 10_000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
