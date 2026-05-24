import { createHash } from "node:crypto";

/**
 * Deterministic room-name derivation from a pair of Firebase Auth UIDs.
 *
 * Per shared/auth-proxy-api.md:
 * > Deterministic name: call-<base32(sha256(sorted_uids_joined_with_dash))>
 * > truncated to 32 chars. Both partners hashing the same sorted-uid set
 * > yields the same `roomName` regardless of who calls first.
 *
 * The truncation budget: 32 chars total - "call-" prefix (5) = 27 chars of hash.
 * Base32-encoded SHA-256 (32 bytes) is 52 chars — we take the first 27.
 *
 * Stability: same input pair → same output, byte-for-byte, forever. The hash
 * is treated as identity material; do NOT change the algorithm without a
 * coordinated client-side migration (would orphan all in-flight calls).
 */
const ROOM_NAME_MAX_LENGTH = 32;
const ROOM_NAME_PREFIX = "call-";
const HASH_BUDGET = ROOM_NAME_MAX_LENGTH - ROOM_NAME_PREFIX.length;

// RFC 4648 base32 alphabet (no padding). LiveKit room names are ASCII-safe
// alphanumeric, so this avoids the `+` and `/` chars from base64.
const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function base32Encode(bytes: Uint8Array): string {
    let bits = 0;
    let value = 0;
    let output = "";
    for (const byte of bytes) {
        value = (value << 8) | byte;
        bits += 8;
        while (bits >= 5) {
            const idx = (value >>> (bits - 5)) & 0x1f;
            output += BASE32_ALPHABET[idx];
            bits -= 5;
        }
    }
    if (bits > 0) {
        const idx = (value << (5 - bits)) & 0x1f;
        output += BASE32_ALPHABET[idx];
    }
    return output;
}

/**
 * Compute the deterministic room name for a pair (uidA, uidB). Order of
 * arguments does NOT matter — sorted internally before hashing.
 */
export function roomNameForPair(uidA: string, uidB: string): string {
    if (!uidA || !uidB) {
        throw new Error("roomNameForPair: both uids required");
    }
    if (uidA === uidB) {
        throw new Error("roomNameForPair: requester and peer uid cannot be the same");
    }
    const sorted = [uidA, uidB].sort().join("-");
    const hash = createHash("sha256").update(sorted).digest();
    const encoded = base32Encode(hash).slice(0, HASH_BUDGET);
    return `${ROOM_NAME_PREFIX}${encoded}`;
}
