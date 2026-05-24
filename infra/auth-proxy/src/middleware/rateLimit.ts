import type { NextFunction, Request, Response } from "express";
import { ApiError, ErrorCode } from "../types.js";

/**
 * Per-UID sliding-window rate limiter — 10 tokens per 60 seconds.
 *
 * Per shared/auth-proxy-api.md: "429 | ERR_RATE_LIMITED, retryAfterMs": 5000".
 *
 * Storage: in-memory `Map<uid, timestamps[]>`. Single-process v1; if we ever
 * scale to multiple auth-proxy replicas, swap for Redis-backed (LiveKit's
 * Redis is already in the compose stack).
 *
 * Eviction: lazy — on each check, drop timestamps older than the window.
 * No background sweeper; entries for inactive UIDs eventually get pruned
 * the next time that UID makes a request (memory growth is bounded by
 * active-UID count, which for a 2-user pair app is at most ~2-20 in practice).
 */

const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 10;
const RETRY_AFTER_MS = 5_000;

export interface RateLimiterState {
    /** Map of uid → array of request timestamps (ms-since-epoch) within the window. */
    buckets: Map<string, number[]>;
    /** Time source — injected for tests. Defaults to Date.now. */
    now: () => number;
}

export function createRateLimiterState(now: () => number = Date.now): RateLimiterState {
    return { buckets: new Map(), now };
}

/**
 * Module-level default state. Production-side: shared across all requests.
 * Test-side: each test creates its own [createRateLimiterState] + uses
 * [createRateLimiter] to inject it.
 */
const defaultState = createRateLimiterState();

/**
 * Returns the count of requests allowed for `uid` after this call. If the
 * count would exceed [MAX_REQUESTS_PER_WINDOW], throws [ApiError] with
 * `ERR_RATE_LIMITED`.
 *
 * Pure function (apart from state mutation) — easy to unit-test by injecting
 * [createRateLimiterState] with a fake clock.
 */
export function checkRateLimit(uid: string, state: RateLimiterState = defaultState): void {
    const nowMs = state.now();
    const cutoff = nowMs - WINDOW_MS;
    const existing = state.buckets.get(uid) ?? [];
    // Drop timestamps outside the window (lazy eviction).
    const inWindow = existing.filter((t) => t >= cutoff);
    if (inWindow.length >= MAX_REQUESTS_PER_WINDOW) {
        throw new ApiError(ErrorCode.RateLimited, 429, "rate limit exceeded", { retryAfterMs: RETRY_AFTER_MS });
    }
    inWindow.push(nowMs);
    state.buckets.set(uid, inWindow);
}

/**
 * Express middleware factory. Closes over a [RateLimiterState] (defaults to
 * the module-level singleton). Tests inject their own state.
 */
export function rateLimitMiddleware(state: RateLimiterState = defaultState) {
    return function rateLimit(req: Request, _res: Response, next: NextFunction): void {
        const uid = req.auth?.uid;
        if (!uid) {
            next(new ApiError(ErrorCode.InvalidRequest, 400, "rate limit requires authenticated requester"));
            return;
        }
        try {
            checkRateLimit(uid, state);
            next();
        } catch (e) {
            next(e);
        }
    };
}
