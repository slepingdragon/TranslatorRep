import { describe, it, expect, vi, beforeEach } from "vitest";
import type { NextFunction, Request, Response } from "express";
import { ApiError, ErrorCode } from "../src/types.js";

// ────────────────────────────────────────────────────────────────────────
// Mock firebase-admin BEFORE importing the middleware under test.
// Vitest hoists vi.mock to the top of the file, so the order in source
// doesn't matter — but the mock factory must be self-contained
// (no out-of-scope references) per vitest docs.
// ────────────────────────────────────────────────────────────────────────

interface MockSnapshot {
    empty: boolean;
}
interface MockQuery {
    where(field: string, op: string, value: unknown): MockQuery;
    limit(n: number): MockQuery;
    get(): Promise<MockSnapshot>;
}

let pairsReturnsEmpty: boolean[] = [];
// Each invocation of collection("pairs") gets a query that returns the next
// pairsReturnsEmpty[i] when .get() is called. Tests set this array before
// invoking the middleware.

const mockFirestore = {
    collection(name: string): MockQuery {
        if (name !== "pairs") {
            throw new Error(`unexpected collection: ${name}`);
        }
        let queryIndex = pairsReturnsEmpty.length === 0 ? 0 : pairsReturnsEmpty.length - 1;
        const q: MockQuery = {
            where: () => q,
            limit: () => q,
            get: async () => {
                const empty = pairsReturnsEmpty.shift() ?? true;
                queryIndex++;
                return { empty };
            },
        };
        return q;
    },
};

vi.mock("../src/firebaseAdmin.js", () => ({
    firebaseApp: () => ({
        firestore: () => mockFirestore,
    }),
    initFirebaseAdmin: () => ({ firestore: () => mockFirestore }),
}));

// Now safe to import the middleware.
const { pairedMiddleware } = await import("../src/middleware/paired.js");

// ────────────────────────────────────────────────────────────────────────

function buildReq(requesterUid: string | undefined, peerUid: unknown): Request {
    return {
        auth: requesterUid ? { uid: requesterUid } : undefined,
        body: { peerUid },
    } as unknown as Request;
}

describe("pairedMiddleware", () => {
    beforeEach(() => {
        pairsReturnsEmpty = [];
    });

    it("calls next() with no error when (requester, peer) pair exists as memberA→memberB", async () => {
        pairsReturnsEmpty = [false]; // first query (memberA=requester, memberB=peer) finds doc
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq("alice", "bob"), {} as Response, next);
        expect(next).toHaveBeenCalledTimes(1);
        expect(next).toHaveBeenCalledWith();
    });

    it("calls next() with no error when pair exists as memberA=peer, memberB=requester (swap)", async () => {
        pairsReturnsEmpty = [true, false]; // first query empty, second (swap) finds doc
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq("alice", "bob"), {} as Response, next);
        expect(next).toHaveBeenCalledTimes(1);
        expect(next).toHaveBeenCalledWith();
    });

    it("throws ERR_NOT_PAIRED when neither query finds a doc", async () => {
        pairsReturnsEmpty = [true, true]; // both empty
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq("alice", "bob"), {} as Response, next);
        expect(next).toHaveBeenCalledTimes(1);
        const arg = (next as unknown as { mock: { calls: unknown[][] } }).mock.calls[0]?.[0];
        expect(arg).toBeInstanceOf(ApiError);
        expect((arg as ApiError).code).toBe(ErrorCode.NotPaired);
        expect((arg as ApiError).status).toBe(403);
    });

    it("throws ERR_NOT_PAIRED with 'cannot pair with self' message when requester == peer", async () => {
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq("alice", "alice"), {} as Response, next);
        const arg = (next as unknown as { mock: { calls: unknown[][] } }).mock.calls[0]?.[0];
        expect(arg).toBeInstanceOf(ApiError);
        expect((arg as ApiError).code).toBe(ErrorCode.NotPaired);
    });

    it("throws ERR_INVALID_REQUEST when req.auth is missing (auth middleware didn't run)", async () => {
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq(undefined, "bob"), {} as Response, next);
        const arg = (next as unknown as { mock: { calls: unknown[][] } }).mock.calls[0]?.[0];
        expect(arg).toBeInstanceOf(ApiError);
        expect((arg as ApiError).code).toBe(ErrorCode.InvalidRequest);
    });

    it("throws ERR_INVALID_REQUEST when peerUid is non-string", async () => {
        const next = vi.fn() as unknown as NextFunction;
        await pairedMiddleware(buildReq("alice", 123), {} as Response, next);
        const arg = (next as unknown as { mock: { calls: unknown[][] } }).mock.calls[0]?.[0];
        expect(arg).toBeInstanceOf(ApiError);
        expect((arg as ApiError).code).toBe(ErrorCode.InvalidRequest);
    });
});
