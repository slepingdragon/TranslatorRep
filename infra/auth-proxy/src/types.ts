import { z } from "zod";

/**
 * Wire types — shape EXACTLY matches shared/auth-proxy-api.md.
 * Do NOT change field names or types without bumping the path-version
 * (`/v1/*` → `/v2/*`) and updating shared/auth-proxy-api.md in the same PR.
 */

// ── Request ─────────────────────────────────────────────────────────────
export const tokenRequestSchema = z.object({
    firebaseIdToken: z.string().min(1, "firebaseIdToken required"),
    // Allowed to be EMPTY: the client sends "" when it can't obtain an App Check
    // token (emulators have no Play Integrity; debug builds without a registered
    // debug token). Enforcement is governed by APP_CHECK_ENFORCED in authMiddleware,
    // NOT here — a non-empty `.min(1)` guard at the parse stage would reject the
    // empty token with ERR_INVALID_REQUEST *before* enforcement is consulted,
    // making APP_CHECK_ENFORCED=false useless for testing. When enforcement IS on,
    // an empty/invalid token is rejected by `verifyToken` → ERR_APP_CHECK_INVALID.
    appCheckToken: z.string(),
    callType: z.enum(["audio", "video"]),
    peerUid: z.string().min(1, "peerUid required"),
});

export type TokenRequest = z.infer<typeof tokenRequestSchema>;

// ── Response ─────────────────────────────────────────────────────────────
export interface TokenResponse {
    livekitJwt: string;
    roomName: string;
    expiresAt: string; // ISO-8601
    livekitWsUrl: string;
}

// ── Error codes ─────────────────────────────────────────────────────────
export const ErrorCode = {
    InvalidRequest: "ERR_INVALID_REQUEST",
    FirebaseTokenInvalid: "ERR_FIREBASE_TOKEN_INVALID",
    AppCheckInvalid: "ERR_APP_CHECK_INVALID",
    NotPaired: "ERR_NOT_PAIRED",
    RateLimited: "ERR_RATE_LIMITED",
    Internal: "ERR_INTERNAL",
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];

/**
 * API error type — middleware/routes throw these; the global error handler
 * formats them into the JSON shape from shared/auth-proxy-api.md.
 */
export class ApiError extends Error {
    public readonly code: ErrorCodeValue;
    public readonly status: number;
    public readonly extra?: Record<string, unknown>;

    constructor(code: ErrorCodeValue, status: number, message?: string, extra?: Record<string, unknown>) {
        super(message ?? code);
        this.name = "ApiError";
        this.code = code;
        this.status = status;
        this.extra = extra;
    }
}

/** Authenticated request — populated by the auth middleware after token verification. */
export interface AuthContext {
    /** Firebase Auth UID of the requester. Verified, trustworthy. */
    uid: string;
}
