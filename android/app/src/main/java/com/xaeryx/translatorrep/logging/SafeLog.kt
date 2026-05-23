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
        val stringified = value.toString()

        if (com.xaeryx.translatorrep.BuildConfig.DEBUG) {
            // Debug-build local log — AGP-generated BuildConfig.DEBUG gates this.
            // R8 strips it in release once minification is on (post Epic 4).
            Log.d(LOG_TAG, "$wireKey=$stringified")
        } else {
            // Release-build Crashlytics custom key. runCatching swallows
            // IllegalStateException raised when FirebaseCrashlytics.getInstance()
            // is called before Firebase init (Story 1.4 wires the init).
            runCatching {
                FirebaseCrashlytics.getInstance().setCustomKey(wireKey, stringified)
            }
        }
    }
}
