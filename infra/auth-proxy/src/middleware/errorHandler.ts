import type { NextFunction, Request, Response } from "express";
import { logger } from "../lib/logger.js";
import { ApiError, ErrorCode } from "../types.js";

/**
 * Express error handler — formats [ApiError] into the JSON shape per
 * shared/auth-proxy-api.md's error-responses table.
 *
 * Unknown errors get logged + mapped to a generic 500 `ERR_INTERNAL` so we
 * never leak stack traces or internal SDK messages to the client.
 *
 * Express's "error middleware" is identified by its 4-arg signature; do NOT
 * change the parameter count even if `_next` is unused.
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
    if (err instanceof ApiError) {
        const body: Record<string, unknown> = { error: err.code };
        if (err.code === ErrorCode.InvalidRequest && err.message && err.message !== err.code) {
            body.message = err.message;
        }
        if (err.extra) {
            Object.assign(body, err.extra);
        }
        res.status(err.status).json(body);
        return;
    }

    // Unknown error — log + 500.
    const msg = err instanceof Error ? err.message : String(err);
    const stack = err instanceof Error ? err.stack : undefined;
    logger.error({ outcome: ErrorCode.Internal, reason: msg, stack }, "unhandled error");
    res.status(500).json({ error: ErrorCode.Internal });
}
