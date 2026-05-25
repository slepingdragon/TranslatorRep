import { z } from "zod";

/**
 * Process env validation + parsing. Fails fast at boot if any required var is
 * missing/malformed — preferable to mysterious runtime errors deep in a request
 * handler when an env var turns out to be undefined.
 *
 * See infra/.env.example for the canonical list + how to populate each value.
 */
const envSchema = z.object({
    NODE_ENV: z.enum(["production", "development", "test"]).default("development"),
    PORT: z.coerce.number().int().positive().default(3000),

    // LiveKit Cloud API key + secret — used to sign minted JWTs. From the LiveKit
    // Cloud project (Project → Settings → Keys). Set as Render secret env vars.
    LIVEKIT_API_KEY: z.string().min(8, "LIVEKIT_API_KEY too short (LiveKit requires ≥8 chars)"),
    LIVEKIT_API_SECRET: z.string().min(16, "LIVEKIT_API_SECRET too short (≥16 chars recommended)"),

    // LiveKit Cloud project WebSocket URL (wss://<project>.livekit.cloud) the mobile
    // client connects to. Embedded in the token response. Required (no default — set
    // per environment; Render injects it). See docs/runbooks/livekit-cloud-render-setup.md.
    LIVEKIT_WS_URL: z.string().url(),

    // Firebase Admin SDK setup. PROJECT_ID is used for verifying ID tokens
    // (issued by the same project) and for App Check JWKS path resolution.
    FIREBASE_PROJECT_ID: z.string().min(1, "FIREBASE_PROJECT_ID required"),

    // Service account JSON (base64 to fit in a one-line env var). The Admin
    // SDK is initialized with this credential — gives the auth-proxy read
    // access to Firestore /pairs/{pairId} and the ability to verify ID +
    // App Check tokens server-side.
    FIREBASE_SERVICE_ACCOUNT_JSON_BASE64: z.string().min(1, "FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 required"),

    // When "false", /v1/token SKIPS App Check verification (it still requires a
    // valid Firebase ID token — i.e. a real signed-in user). Set to "false" on
    // Render during dev/testing so debug builds don't need a per-device App Check
    // debug token registered. Default "true" = production-safe (device attestation
    // enforced). See docs/runbooks/livekit-cloud-render-setup.md.
    APP_CHECK_ENFORCED: z.enum(["true", "false"]).default("true").transform((v) => v === "true"),
});

export type Env = z.infer<typeof envSchema>;

let cached: Env | null = null;

export function getEnv(): Env {
    if (cached) {
        return cached;
    }
    const parsed = envSchema.safeParse(process.env);
    if (!parsed.success) {
        // Fail fast — print compact error + exit.
        const issues = parsed.error.issues.map((i) => `  - ${i.path.join(".")}: ${i.message}`).join("\n");
        // eslint-disable-next-line no-console
        console.error(`[auth-proxy] Invalid environment configuration:\n${issues}`);
        process.exit(1);
    }
    cached = parsed.data;
    return cached;
}

/**
 * Decoded Firebase service account JSON. Parsed once at boot via [getEnv] +
 * this helper; passed to `admin.initializeApp({ credential: cert(...) })`.
 */
export function getServiceAccountJson(): Record<string, unknown> {
    const env = getEnv();
    const decoded = Buffer.from(env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64, "base64").toString("utf-8");
    try {
        return JSON.parse(decoded) as Record<string, unknown>;
    } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        throw new Error(
            `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 didn't decode to valid JSON: ${msg}. ` +
                "Did you base64-encode the file with newlines stripped (\`base64 -w 0\` on linux, " +
                "\`[Convert]::ToBase64String(...)\` on PowerShell)?",
        );
    }
}
