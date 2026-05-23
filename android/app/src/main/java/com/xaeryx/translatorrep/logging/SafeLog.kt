package com.xaeryx.translatorrep.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Privacy-safe logging facade. Every logging call in the project goes through this
 * facade — direct use of [android.util.Log] or `timber.log.Timber` is banned outside
 * this file by the detekt `ForbiddenImport` rule (configured in
 * `android/detekt-config.yml`; the rule excludes the `logging/SafeLog.kt` file via
 * the glob `[double-asterisk]/logging/SafeLog.kt`).
 *
 * The allowlist is [AllowedLogKey] — adding a new key requires an ADR amendment plus a
 * simultaneous iOS PR. The forbidden surfaces are:
 *
 * - Conversation content: `source_text`, `target_text`, `caption_text`, any text the
 *   user spoke or that was translated. Never log a value containing these.
 * - PII: `participant_name`, `display_name`, real-name identifiers.
 * - Flores-200 language codes (`ind_Latn`, `eng_Latn`, `sun_Latn`, etc.) — these stay
 *   inside `TranslationProvider.translate()` and never reach logs, telemetry, or
 *   error metadata. BCP 47 codes (`id-ID`, `en-US`) are fine.
 *
 * Two output routes:
 *
 * 1. Crashlytics custom keys (release builds only) — appears in crash reports' "Keys"
 *    section under the snake_case wire form of the [AllowedLogKey]. Gated on
 *    `!BuildConfig.DEBUG` so debug-session noise never lands in the production
 *    Crashlytics dashboard. Wrapped in [runCatching] so pre-Firebase-init release
 *    builds (anything before Story 1.4 finishes) no-op gracefully rather than crash.
 * 2. android.util.Log.d (debug builds, via `BuildConfig.DEBUG`) — for local
 *    development visibility. In release builds R8 strips this once `isMinifyEnabled`
 *    is flipped (post Epic 4).
 *
 * See architecture.md §14, /shared/canonical-names.md §2, and Story 1.5 dev notes.
 */
object SafeLog {

    private const val LOG_TAG = "TranslatorRep"

    /**
     * Log a single event with one allowed key + one value. The [value] is converted
     * to its String form for Crashlytics and Log.d. Callers MUST NOT pass conversation
     * content, PII, or Flores codes as [value] — there is no runtime enforcement of
     * this (impossible without parsing); the rule is enforced by code review (§16).
     */
    fun event(key: AllowedLogKey, value: Any) {
        val wireKey = key.wireKey
        val stringified = stringifyDefensively(value)

        if (com.xaeryx.translatorrep.BuildConfig.DEBUG) {
            // Debug-build local log — AGP-generated BuildConfig.DEBUG gates this.
            // R8 strips it in release once minification is on (post Epic 4).
            Log.d(LOG_TAG, "$wireKey=$stringified")
        } else {
            // Release-build Crashlytics custom key. The catch is intentionally broad
            // (RuntimeException) so the facade no-ops on:
            //   - IllegalStateException — FirebaseCrashlytics.getInstance() called
            //     before Firebase init (release builds before Story 1.4 wires init).
            //   - Any other RuntimeException — defensive: a buggy logging path must
            //     NOT crash the caller. Availability > visibility here.
            // Throwable is NOT caught so OOM / StackOverflow propagate normally.
            @Suppress("TooGenericExceptionCaught")
            try {
                FirebaseCrashlytics.getInstance().setCustomKey(wireKey, stringified)
            } catch (e: RuntimeException) {
                // Intentional broad swallow — see comment above. The `e` parameter
                // is kept (not `_`) so the suppression remains explicit per detekt.
                @Suppress("UnusedPrivateMember") val ignored = e
            }
        }
    }

    /**
     * Convert [value] to a String, never propagating an exception from a buggy
     * [Any.toString] override. A caller's broken toString must not turn a logging
     * call into a fault source — return a marker string instead so the event still
     * lands (degraded but visible) and the caller continues.
     */
    private fun stringifyDefensively(value: Any): String =
        @Suppress("TooGenericExceptionCaught")
        try {
            value.toString()
        } catch (e: RuntimeException) {
            "<toString-failed:${e.javaClass.simpleName}>"
        }
}
