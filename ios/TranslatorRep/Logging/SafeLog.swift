// SafeLog.swift
// TranslatorRep — Story 1.5
//
// Privacy-safe logging facade. Every logging call in the project goes through this
// struct — direct use of `print`, `os_log`, or `Logger` is banned outside this file
// by the SwiftLint `forbid_direct_ios_logging` custom rule (configured in
// /ios/.swiftlint.yml; rule exclusion is `Logging/SafeLog\.swift`).
//
// The allowlist is AllowedLogKey — adding a new key requires an ADR amendment plus
// a simultaneous Android PR. Forbidden values:
//
//   - Conversation content: source_text, target_text, caption_text — any text the
//     user spoke or that was translated.
//   - PII: participant_name, display_name, real-name identifiers.
//   - Flores-200 language codes (ind_Latn, eng_Latn, sun_Latn, etc.) — these stay
//     inside TranslationProvider.translate() and never reach logs, telemetry, or
//     error metadata. BCP 47 codes (id-ID, en-US) are fine.
//
// Two output routes:
//
//   1. Crashlytics custom keys (release builds only) — appears in crash reports'
//      "Keys" section under the snake_case wire form of the AllowedLogKey. Mirrors
//      the Android facade which gates Crashlytics on `!BuildConfig.DEBUG` — debug
//      sessions never pollute the production Crashlytics dashboard.
//   2. os_log (DEBUG builds, gated by #if DEBUG) — for Console.app visibility during
//      development. Uses `%@` (the default) which respects iOS unified-log privacy
//      redaction; values appear as `<private>` in sysdiagnose/MDM diagnostic
//      captures rather than in plaintext.
//
// Crashlytics.crashlytics() is safe to call before FirebaseApp.configure() — on
// iOS the component lookup logs an error and returns a nil-backed singleton;
// downstream `setCustomValue` calls dispatch to nil (safe no-op in Obj-C
// msgSend semantics). No do/catch wrap is needed (and would not compile —
// `setCustomValue(_:forKey:)` is non-throwing — nor would it catch Obj-C
// NSException which Swift cannot trap).
//
// See architecture.md §14, /shared/canonical-names.md §2, Story 1.5 dev notes.

import Foundation
import os                            // for os_log — the ONLY allowed use in the app
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif

struct SafeLog {

    private static let logger = OSLog(subsystem: bundleIdentifier, category: "SafeLog")

    /// Bundle identifier for OSLog subsystem. Resolves at runtime from
    /// `Bundle.main.bundleIdentifier`; falls back to the architecture-locked value
    /// `com.xaeryx.translatorrep` if the bundle ID isn't available (test targets,
    /// command-line tools). Story 1.2 sets the real bundle identifier in Xcode.
    private static var bundleIdentifier: String {
        Bundle.main.bundleIdentifier ?? "com.xaeryx.translatorrep"
    }

    /// Log a single event with one allowed key + one value. The `value` is converted
    /// to its String form for both routes. Callers MUST NOT pass conversation content,
    /// PII, or Flores codes as `value` — enforced by code review (architecture §16).
    static func event(_ key: AllowedLogKey, _ value: Any) {
        let wireKey = key.rawValue
        let stringified = String(describing: value)

        #if DEBUG
        // `%@` (default privacy) lets iOS redact the dynamic value to `<private>`
        // in sysdiagnose/MDM-collected diagnostics; explicit `%{public}@` would
        // strip that defense-in-depth and is intentionally avoided.
        os_log("%@=%@", log: logger, type: .debug, wireKey, stringified)
        #endif

        // Crashlytics route — release builds only. Mirrors Android's
        // `!BuildConfig.DEBUG` gate so debug-session noise never lands in the
        // production Crashlytics dashboard.
        #if !DEBUG
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().setCustomValue(stringified, forKey: wireKey)
        #endif
        #endif
    }
}
