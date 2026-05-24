import type { NextFunction, Request, Response } from "express";
import { firebaseApp } from "../firebaseAdmin.js";
import { logger } from "../lib/logger.js";
import { ApiError, ErrorCode } from "../types.js";

/**
 * Verify the authenticated requester is paired with `req.body.peerUid` per
 * Firestore `/pairs/{pairId}`.
 *
 * **Pairing model:** any `/pairs/{pairId}` doc has fields `memberA: string` +
 * `memberB: string` (per firebase/firestore.rules). A request is "paired" if
 * the doc's two members are exactly {requester.uid, peerUid} in some order.
 *
 * **Lookup strategy:** there's no deterministic pairId we can compute (pair IDs
 * are server-assigned ULIDs at pairing time, not derived). So we query the
 * collection: `where('memberA', 'in', [requesterUid, peerUid])` then filter
 * client-side for the doc whose `memberB` is the other uid. With at most one
 * pair per uid in v1 (architecture §"Pairing — exactly one peer per device"),
 * this is a O(1) read in practice.
 *
 * Throws [ApiError] `ERR_NOT_PAIRED` (403) if no matching pair found.
 */
export async function pairedMiddleware(req: Request, _res: Response, next: NextFunction): Promise<void> {
    const requesterUid = req.auth?.uid;
    const peerUid = (req.body as { peerUid?: unknown } | undefined)?.peerUid;
    if (!requesterUid || typeof peerUid !== "string") {
        // Defensive — auth middleware should have populated req.auth; zod should
        // have validated peerUid. Treat as ERR_INVALID_REQUEST.
        next(new ApiError(ErrorCode.InvalidRequest, 400, "missing auth context or peerUid"));
        return;
    }
    if (requesterUid === peerUid) {
        next(new ApiError(ErrorCode.NotPaired, 403, "cannot pair with self"));
        return;
    }

    try {
        const app = firebaseApp();
        const db = app.firestore();
        // Try the symmetric pair-doc structure. Most cost-effective query: filter
        // by memberA == requester (returns ≤1 doc since v1 enforces 1 pair/uid)
        // and check memberB; if no hit, swap.
        const aThenB = await db
            .collection("pairs")
            .where("memberA", "==", requesterUid)
            .where("memberB", "==", peerUid)
            .limit(1)
            .get();
        if (!aThenB.empty) {
            next();
            return;
        }
        const bThenA = await db
            .collection("pairs")
            .where("memberA", "==", peerUid)
            .where("memberB", "==", requesterUid)
            .limit(1)
            .get();
        if (!bThenA.empty) {
            next();
            return;
        }

        logger.info({ outcome: ErrorCode.NotPaired }, "no matching /pairs/ doc");
        next(new ApiError(ErrorCode.NotPaired, 403));
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.error({ outcome: ErrorCode.Internal, reason: msg }, "paired middleware crashed");
        next(new ApiError(ErrorCode.Internal, 500));
    }
}
