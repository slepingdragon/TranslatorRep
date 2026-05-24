import { AccessToken } from "livekit-server-sdk";
import { getEnv } from "../env.js";

/**
 * Wrapper around LiveKit's AccessToken minting. Keeps the SDK call site in
 * one place so test mocks can target a single import + the JWT shape stays
 * consistent with shared/auth-proxy-api.md ("includes claims: sub, roomJoin,
 * room, metadata").
 */

/** TTL of the minted JWT in seconds. Spec says ≤60s. */
const TOKEN_TTL_SECONDS = 60;

export interface MintTokenInput {
    /** Authenticated Firebase Auth UID — becomes the JWT `sub` + LiveKit identity. */
    identity: string;
    /** Deterministic room name from [roomNameForPair]. */
    roomName: string;
    /** Audio vs video — embedded in JWT metadata claim per the API contract. */
    callType: "audio" | "video";
}

export interface MintedToken {
    /** Signed JWT (string). */
    jwt: string;
    /** ISO-8601 wall-clock expiration. */
    expiresAt: string;
}

/**
 * Mint a short-lived LiveKit JWT.
 */
export async function mintLivekitToken(input: MintTokenInput): Promise<MintedToken> {
    const env = getEnv();
    const token = new AccessToken(env.LIVEKIT_API_KEY, env.LIVEKIT_API_SECRET, {
        identity: input.identity,
        ttl: TOKEN_TTL_SECONDS,
        metadata: JSON.stringify({ callType: input.callType }),
    });
    token.addGrant({
        roomJoin: true,
        room: input.roomName,
        canPublish: true,
        canSubscribe: true,
        canPublishData: true, // Data Channel for translation captions (Epic 4)
    });
    const jwt = await token.toJwt();
    const expiresAt = new Date(Date.now() + TOKEN_TTL_SECONDS * 1000).toISOString();
    return { jwt, expiresAt };
}
