import pino from "pino";
import { getEnv } from "../env.js";

/**
 * Structured JSON logger — captured by `docker logs translatorrep-authproxy`.
 *
 * Per shared/auth-proxy-api.md "Logging Contract": log fields per request are
 * `request_id`, `requester_uid_hash`, `call_type`, `outcome`, `latency_ms`.
 * NEVER log raw UIDs, tokens, peer UIDs, or request body content.
 */
const env = getEnv();

export const logger = pino({
    level: env.NODE_ENV === "production" ? "info" : "debug",
    // Pino's default JSON format. In development, prettify only if `pino-pretty`
    // is installed (it isn't in package.json — keep prod-style logs everywhere
    // so dev troubleshooting matches what runs in the VM).
    formatters: {
        level(label) {
            // String level name instead of numeric, for human-readable Docker logs.
            return { level: label };
        },
    },
    // Don't include hostname (the container hostname is meaningless inside
    // docker-compose; the service name is enough context).
    base: { service: "auth-proxy" },
});
