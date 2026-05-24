import type { Request, Response } from "express";

/**
 * GET /v1/healthz — uptime + status. Used by Docker healthcheck +
 * Caddy upstream check. No auth required.
 *
 * Per shared/auth-proxy-api.md §"Health Check":
 *   200 OK with body {"status":"ok","uptimeSeconds":<integer>}
 */
const bootTimeMs = Date.now();

export function healthzHandler(_req: Request, res: Response): void {
    const uptimeSeconds = Math.floor((Date.now() - bootTimeMs) / 1000);
    res.status(200).json({ status: "ok", uptimeSeconds });
}
