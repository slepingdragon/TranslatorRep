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
 * 1. Crashlytics custom keys (production builds) — appears in crash reports' "Keys"
 *    section under the snake_case wire form of the [AllowedLogKey]. Wrapped in
 *    [runCatching] so pre-Firebase-init builds (anything before Story 1.4 finishes)
 *    no-op gracefully rather than crash.
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

        // Debug-build local log — gated by Android's BuildConfig.DEBUG which the AGP
        // plugin generates per module. R8 strips this in release once minification is on.
        if (com.xaeryx.translatorrep.BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "$wireKey=$stringified")
        }

        // Crashlytics route — robust to pre-init state (Story 1.4 wires
        // FirebaseApp.initializeApp; before then this getInstance call may throw
        // IllegalStateException which we swallow).
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey(wireKey, stringified)
        }
    }
}
