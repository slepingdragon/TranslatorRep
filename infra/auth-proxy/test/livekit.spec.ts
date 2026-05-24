import { describe, it, expect, beforeAll } from "vitest";
import { mintLivekitToken } from "../src/lib/livekit.js";

// Stub env BEFORE importing modules that read env via getEnv().
// In a real isolated test we'd inject env via DI; for now use process.env
// since vitest doesn't reset it between files by default.
beforeAll(() => {
    process.env.LIVEKIT_API_KEY = "APIxxxxxxxxxxxx";
    process.env.LIVEKIT_API_SECRET = "0123456789abcdef0123456789abcdef";
    process.env.LIVEKIT_WS_URL = "wss://sfu.xaeryx.com";
    process.env.FIREBASE_PROJECT_ID = "test-project";
    process.env.FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 = Buffer.from(
        JSON.stringify({
            type: "service_account",
            project_id: "test-project",
            // The Admin SDK requires a private key for cert init; we never actually
            // call Admin SDK in this test, but the env validator runs at import time.
            private_key_id: "x",
            private_key: "-----BEGIN PRIVATE KEY-----\nFAKE\n-----END PRIVATE KEY-----\n",
            client_email: "test@test-project.iam.gserviceaccount.com",
            client_id: "x",
            auth_uri: "https://accounts.google.com/o/oauth2/auth",
            token_uri: "https://oauth2.googleapis.com/token",
            auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
            client_x509_cert_url: "https://www.googleapis.com/robot/v1/metadata/x509/test%40test-project.iam.gserviceaccount.com",
        }),
    ).toString("base64");
});

describe("mintLivekitToken", () => {
    it("returns a non-empty JWT", async () => {
        const result = await mintLivekitToken({
            identity: "alice",
            roomName: "call-test",
            callType: "audio",
        });
        expect(result.jwt).toBeTruthy();
        expect(result.jwt.split(".")).toHaveLength(3); // JWT = header.payload.signature
    });

    it("sets expiresAt approximately 60s in the future", async () => {
        const before = Date.now();
        const result = await mintLivekitToken({
            identity: "alice",
            roomName: "call-test",
            callType: "audio",
        });
        const after = Date.now();
        const expiresAtMs = new Date(result.expiresAt).getTime();
        // Bounds: at least (before + 59s), at most (after + 61s).
        expect(expiresAtMs).toBeGreaterThanOrEqual(before + 59_000);
        expect(expiresAtMs).toBeLessThanOrEqual(after + 61_000);
    });

    it("encodes the callType metadata + room grant in JWT payload", async () => {
        const result = await mintLivekitToken({
            identity: "alice",
            roomName: "call-audiotest",
            callType: "audio",
        });
        // Decode payload (middle segment) — base64url.
        const payloadB64Url = result.jwt.split(".")[1];
        expect(payloadB64Url).toBeDefined();
        const payloadB64 = (payloadB64Url as string).replace(/-/g, "+").replace(/_/g, "/");
        const payload = JSON.parse(Buffer.from(payloadB64, "base64").toString("utf-8")) as Record<string, unknown>;
        expect(payload.sub).toBe("alice");
        expect(payload.metadata).toBe(JSON.stringify({ callType: "audio" }));
        // `video` grant block contains roomJoin + room name.
        const video = payload.video as { roomJoin?: boolean; room?: string } | undefined;
        expect(video?.roomJoin).toBe(true);
        expect(video?.room).toBe("call-audiotest");
    });
});
