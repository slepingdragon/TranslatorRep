/**
 * Vitest setup — runs once before any test file. Stubs the env vars that
 * `src/env.ts` requires at module-load time (the logger module eagerly calls
 * `getEnv()` at import to set its log level, so any test file that touches
 * a module that touches the logger needs valid env).
 *
 * Tests that want to override these (e.g., to test specific env-parsing
 * paths) can do so in their own `beforeAll`/`beforeEach` hooks.
 */
process.env.NODE_ENV = "test";
process.env.LIVEKIT_API_KEY = "APIxxxxxxxxxxxx";
process.env.LIVEKIT_API_SECRET = "0123456789abcdef0123456789abcdef";
process.env.LIVEKIT_WS_URL = "wss://test-project.livekit.cloud";
process.env.FIREBASE_PROJECT_ID = "test-project";
process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 = Buffer.from(
    JSON.stringify({
        type: "service_account",
        project_id: "test-project",
        private_key_id: "x",
        private_key: "-----BEGIN PRIVATE KEY-----\nFAKE\n-----END PRIVATE KEY-----\n",
        client_email: "test@test-project.iam.gserviceaccount.com",
        client_id: "x",
        auth_uri: "https://accounts.google.com/o/oauth2/auth",
        token_uri: "https://oauth2.googleapis.com/token",
        auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
        client_x509_cert_url:
            "https://www.googleapis.com/robot/v1/metadata/x509/test%40test-project.iam.gserviceaccount.com",
    }),
).toString("base64");
