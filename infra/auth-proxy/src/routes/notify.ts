import type { NextFunction, Request, Response } from "express";
import { createHash } from "node:crypto";
import { firebaseApp } from "../firebaseAdmin.js";
import { sendIncomingCallPush } from "../lib/fcm.js";
import { logger } from "../lib/logger.js";
import { ApiError, ErrorCode } from "../types.js";
import { authMiddleware } from "../middleware/auth.js";
import { pairedMiddleware } from "../middleware/paired.js";
import { rateLimitMiddleware } from "../middleware/rateLimit.js";
import { parseTokenRequestMiddleware } from "./token.js";

/**
 * POST /v1/notify — ring the partner when they may be backgrounded (Story 2.5). Same body +
 * middleware chain as /v1/token (validate → auth → rate-limit → paired): the caller must be a
 * signed-in, App-Check-verified member paired with `peerUid`. Looks up the peer's FCM token from
 * `/users/{peerUid}.fcmToken` and sends a high-priority data push.
 *
 * Best-effort by design: a missing token or a send failure returns 200 `{delivered:false}` rather
 * than an error — the caller's call (and the in-app foreground ring) must not fail over a push.
 */
export async function notifyHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
    const requesterUid = req.auth?.uid;
    const body = req.body as { callType: "audio" | "video"; peerUid: string };
    if (!requesterUid) {
        next(new ApiError(ErrorCode.Internal, 500, "auth context missing at handler"));
        return;
    }
    try {
        const db = firebaseApp().firestore();
        const peerDoc = await db.collection("users").doc(body.peerUid).get();
        const fcmToken = peerDoc.get("fcmToken") as string | undefined;
        if (!fcmToken) {
            // Peer never registered a push token (older build / notifications off). Not an error.
            logger.info({ requester_uid_hash: hashUid(requesterUid), outcome: "no_token" }, "notify skipped");
            res.status(200).json({ delivered: false });
            return;
        }
        await sendIncomingCallPush({ fcmToken, callerUid: requesterUid, callType: body.callType });
        logger.info(
            { requester_uid_hash: hashUid(requesterUid), call_type: body.callType, outcome: "delivered" },
            "notify sent",
        );
        res.status(200).json({ delivered: true });
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.error({ reason: msg }, "notify failed");
        // Best-effort — don't fail the caller's call over a push problem.
        res.status(200).json({ delivered: false });
    }
}

/** SHA-256 of UID, hex — log-safe identity (mirrors token.ts). */
function hashUid(uid: string): string {
    return createHash("sha256").update(uid).digest("hex");
}

/** Ordered middleware chain for POST /v1/notify — same shape as /v1/token. */
export const notifyChain = [
    parseTokenRequestMiddleware,
    authMiddleware,
    rateLimitMiddleware(),
    pairedMiddleware,
    notifyHandler,
];
