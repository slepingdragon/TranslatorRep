import type { NextFunction, Request, Response } from "express";
import { getEnv } from "../env.js";
import { mintLivekitToken } from "../lib/livekit.js";
import { logger } from "../lib/logger.js";
import { roomNameForPair } from "../lib/roomName.js";
import { ApiError, ErrorCode, type TokenResponse, tokenRequestSchema } from "../types.js";
import { authMiddleware } from "../middleware/auth.js";
import { pairedMiddleware } from "../middleware/paired.js";
import { rateLimitMiddleware } from "../middleware/rateLimit.js";
import { createHash } from "node:crypto";

/**
 * POST /v1/token — full pipeline:
 *   1. Body schema validation (zod)
 *   2. authMiddleware: verify Firebase ID token + App Check token
 *   3. rateLimitMiddleware: per-UID sliding window (after auth so we know uid)
 *   4. pairedMiddleware: Firestore /pairs/{pairId} membership check
 *   5. Mint LiveKit JWT + return per shared/auth-proxy-api.md
 *
 * Express middleware stack composition lives in src/index.ts; this module
 * exports each handler so the wiring is explicit.
 */

/**
 * Body-parse middleware — runs FIRST to populate req.body for the auth +
 * paired middlewares + the final handler. Throws ERR_INVALID_REQUEST on
 * schema violation.
 */
export function parseTokenRequestMiddleware(req: Request, _res: Response, next: NextFunction): void {
    const parsed = tokenRequestSchema.safeParse(req.body);
    if (!parsed.success) {
        const issue = parsed.error.issues[0];
        const detail = issue ? `${issue.path.join(".")}: ${issue.message}` : "body parse failed";
        next(new ApiError(ErrorCode.InvalidRequest, 400, detail));
        return;
    }
    // Replace body with the parsed (and now type-narrowed) version.
    req.body = parsed.data;
    next();
}

/**
 * Final handler — mint + respond. Runs after all middlewares have verified
 * + populated req.auth. Privacy-safe logging per the spec's "Logging Contract".
 */
export async function tokenHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
    const startMs = Date.now();
    const requesterUid = req.auth?.uid;
    const body = req.body as { callType: "audio" | "video"; peerUid: string };
    if (!requesterUid) {
        next(new ApiError(ErrorCode.Internal, 500, "auth context missing at handler"));
        return;
    }
    try {
        const env = getEnv();
        const roomName = roomNameForPair(requesterUid, body.peerUid);
        const { jwt, expiresAt } = await mintLivekitToken({
            identity: requesterUid,
            roomName,
            callType: body.callType,
        });
        const response: TokenResponse = {
            livekitJwt: jwt,
            roomName,
            expiresAt,
            livekitWsUrl: env.LIVEKIT_WS_URL,
        };
        res.status(200).json(response);

        // Privacy-safe access log — NEVER raw UIDs/tokens/peers; only hash + bucketed fields.
        logger.info(
            {
                requester_uid_hash: hashUid(requesterUid),
                call_type: body.callType,
                outcome: "granted",
                latency_ms: Date.now() - startMs,
            },
            "token minted",
        );
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.error({ reason: msg, latency_ms: Date.now() - startMs }, "token mint failed");
        next(new ApiError(ErrorCode.Internal, 500));
    }
}

/** SHA-256 of UID, hex-encoded — log-safe identity that doesn't leak the raw UID. */
function hashUid(uid: string): string {
    return createHash("sha256").update(uid).digest("hex");
}

/** Ordered middleware chain for POST /v1/token — exported so index.ts can mount. */
export const tokenChain = [
    parseTokenRequestMiddleware,
    authMiddleware,
    rateLimitMiddleware(),
    pairedMiddleware,
    tokenHandler,
];
