import type { NextFunction, Request, Response } from "express";
import { getEnv } from "../env.js";
import { firebaseApp } from "../firebaseAdmin.js";
import { logger } from "../lib/logger.js";
import { ApiError, ErrorCode, type AuthContext } from "../types.js";

declare module "express-serve-static-core" {
    interface Request {
        auth?: AuthContext;
    }
}

/**
 * Verify Firebase ID token + App Check token in parallel.
 *
 * On success: attaches `req.auth = { uid }` and calls next().
 * On failure: throws an [ApiError] with `ERR_FIREBASE_TOKEN_INVALID` (401)
 * or `ERR_APP_CHECK_INVALID` (401) per shared/auth-proxy-api.md.
 *
 * Body-shape validation is the route's job; this middleware reads only
 * `firebaseIdToken` + `appCheckToken` from `req.body`. Routes call this AFTER
 * zod-parsing the body.
 */
export async function authMiddleware(req: Request, _res: Response, next: NextFunction): Promise<void> {
    const body = req.body as { firebaseIdToken?: unknown; appCheckToken?: unknown } | undefined;
    if (!body || typeof body.firebaseIdToken !== "string" || typeof body.appCheckToken !== "string") {
        // Should never happen if zod validation ran first — defensive guard.
        next(new ApiError(ErrorCode.InvalidRequest, 400, "missing firebaseIdToken or appCheckToken"));
        return;
    }
    const app = firebaseApp();
    const enforceAppCheck = getEnv().APP_CHECK_ENFORCED;

    try {
        // Run both verifications in parallel — neither depends on the other,
        // and saves ~50-100ms of round-trip on each request. When App Check
        // enforcement is off (dev/testing), the second slot resolves to null
        // and is never checked — the Firebase ID token is always verified.
        const [idTokenResult, appCheckResult] = await Promise.allSettled([
            app.auth().verifyIdToken(body.firebaseIdToken),
            enforceAppCheck ? app.appCheck().verifyToken(body.appCheckToken) : Promise.resolve(null),
        ]);

        if (idTokenResult.status === "rejected") {
            const reason = idTokenResult.reason instanceof Error ? idTokenResult.reason.message : String(idTokenResult.reason);
            logger.info({ outcome: ErrorCode.FirebaseTokenInvalid, reason }, "id token verification failed");
            next(new ApiError(ErrorCode.FirebaseTokenInvalid, 401));
            return;
        }
        if (enforceAppCheck && appCheckResult.status === "rejected") {
            const reason = appCheckResult.reason instanceof Error ? appCheckResult.reason.message : String(appCheckResult.reason);
            logger.info({ outcome: ErrorCode.AppCheckInvalid, reason }, "app check verification failed");
            next(new ApiError(ErrorCode.AppCheckInvalid, 401));
            return;
        }

        req.auth = { uid: idTokenResult.value.uid };
        next();
    } catch (e) {
        // Catch-all for unexpected SDK-internal errors (Admin SDK initialization
        // failure, JWKS fetch network blip, etc.). Surface as 500 ERR_INTERNAL.
        const msg = e instanceof Error ? e.message : String(e);
        logger.error({ outcome: ErrorCode.Internal, reason: msg }, "auth middleware crashed");
        next(new ApiError(ErrorCode.Internal, 500));
    }
}
