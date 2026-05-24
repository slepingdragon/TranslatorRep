import { z } from "zod";

/**
 * Wire types — shape EXACTLY matches shared/auth-proxy-api.md.
 * Do NOT change field names or types without bumping the path-version
 * (`/v1/*` → `/v2/*`) and updating shared/auth-proxy-api.md in the same PR.
 */

// ── Request ─────────────────────────────────────────────────────────────
export const tokenRequestSchema = z.object({
    firebaseIdToken: z.string().min(1, "firebaseIdToken required"),
    appCheckToken: z.string().min(1, "appCheckToken required"),
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
