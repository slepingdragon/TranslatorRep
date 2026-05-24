import { describe, it, expect } from "vitest";
import { checkRateLimit, createRateLimiterState } from "../src/middleware/rateLimit.js";
import { ApiError, ErrorCode } from "../src/types.js";

describe("checkRateLimit", () => {
    function fakeClock(initialMs: number): { now: () => number; advance: (deltaMs: number) => void } {
        let t = initialMs;
        return {
            now: () => t,
            advance: (deltaMs: number) => {
                t += deltaMs;
            },
        };
    }

    it("allows up to 10 requests within the 60s window", () => {
        const clock = fakeClock(1_000_000);
        const state = createRateLimiterState(clock.now);
        for (let i = 0; i < 10; i++) {
            expect(() => checkRateLimit("alice", state)).not.toThrow();
        }
    });

    it("throws ERR_RATE_LIMITED on the 11th request within the window", () => {
        const clock = fakeClock(1_000_000);
        const state = createRateLimiterState(clock.now);
        for (let i = 0; i < 10; i++) {
            checkRateLimit("alice", state);
        }
        try {
            checkRateLimit("alice", state);
            throw new Error("expected ApiError");
        } catch (e) {
            expect(e).toBeInstanceOf(ApiError);
            const apiErr = e as ApiError;
            expect(apiErr.code).toBe(ErrorCode.RateLimited);
            expect(apiErr.status).toBe(429);
            expect(apiErr.extra?.retryAfterMs).toBe(5000);
        }
    });

    it("isolates per-UID — alice hitting the cap does not affect bob", () => {
        const clock = fakeClock(1_000_000);
        const state = createRateLimiterState(clock.now);
        for (let i = 0; i < 10; i++) {
            checkRateLimit("alice", state);
        }
        // Bob's first request should still succeed.
        expect(() => checkRateLimit("bob", state)).not.toThrow();
    });

    it("evicts old timestamps after the window slides past them", () => {
        const clock = fakeClock(1_000_000);
        const state = createRateLimiterState(clock.now);
        // Fill the bucket at t=0.
        for (let i = 0; i < 10; i++) {
            checkRateLimit("alice", state);
        }
        // Advance the clock past the 60s window.
        clock.advance(61_000);
        // The 11th request that would've failed at t=0 succeeds at t=61s
        // because all 10 prior timestamps are now outside the window.
        expect(() => checkRateLimit("alice", state)).not.toThrow();
    });

    it("partial eviction — first 5 timestamps drop out of window, allowing 5 new", () => {
        const clock = fakeClock(1_000_000);
        const state = createRateLimiterState(clock.now);
        // 5 requests at t=0.
        for (let i = 0; i < 5; i++) {
            checkRateLimit("alice", state);
        }
        // Advance 30s; the 5 are still in window (60s window).
        clock.advance(30_000);
        // 5 more requests — total 10 in the rolling window.
        for (let i = 0; i < 5; i++) {
            checkRateLimit("alice", state);
        }
        // 11th immediately should fail.
        expect(() => checkRateLimit("alice", state)).toThrow();
        // Advance 31s more — the FIRST 5 are now > 60s old → out of window.
        clock.advance(31_000);
        // 5 more requests should succeed.
        for (let i = 0; i < 5; i++) {
            expect(() => checkRateLimit("alice", state)).not.toThrow();
        }
    });
});
