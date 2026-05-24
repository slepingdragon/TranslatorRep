import { describe, it, expect } from "vitest";
import { roomNameForPair } from "../src/lib/roomName.js";

describe("roomNameForPair", () => {
    it("returns identical name regardless of argument order (commutative)", () => {
        const a = roomNameForPair("alice", "bob");
        const b = roomNameForPair("bob", "alice");
        expect(a).toBe(b);
    });

    it("always starts with 'call-' prefix", () => {
        expect(roomNameForPair("alice", "bob")).toMatch(/^call-/);
    });

    it("is exactly 32 chars total (the LiveKit room-name length budget)", () => {
        expect(roomNameForPair("alice", "bob")).toHaveLength(32);
        expect(roomNameForPair("longer-uid-1", "even-longer-uid-2")).toHaveLength(32);
    });

    it("uses only base32 alphabet (A-Z + 2-7) after the prefix", () => {
        const hashPortion = roomNameForPair("alice", "bob").slice("call-".length);
        expect(hashPortion).toMatch(/^[A-Z2-7]+$/);
    });

    it("is deterministic — same input pair yields the same output every call", () => {
        const calls = Array.from({ length: 10 }, () => roomNameForPair("alice", "bob"));
        expect(new Set(calls).size).toBe(1);
    });

    it("produces different names for different pairs", () => {
        const ab = roomNameForPair("alice", "bob");
        const ac = roomNameForPair("alice", "charlie");
        expect(ab).not.toBe(ac);
    });

    it("locked test vector: alice + bob → stable output", () => {
        // If this expectation changes, all in-flight calls between paired users
        // would be orphaned (clients still expect the old room name). Treat as
        // a breaking change requiring a coordinated client-side migration.
        // Vector locked at Story 1.3 Phase 1b — base32-encoded SHA-256("alice-bob"),
        // truncated to 27 chars (the 32-char total budget minus 5-char "call-" prefix).
        const expected = "call-2NXUFQIFKRVGITKYZUHV7YRYYMD";
        expect(roomNameForPair("alice", "bob")).toBe(expected);
    });

    it("throws on missing uid", () => {
        expect(() => roomNameForPair("", "bob")).toThrow(/both uids required/);
        expect(() => roomNameForPair("alice", "")).toThrow(/both uids required/);
    });

    it("throws on identical uids (pair with self disallowed)", () => {
        expect(() => roomNameForPair("alice", "alice")).toThrow(/cannot be the same/);
    });
});
