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
//   1. Crashlytics custom keys (release builds) — appears in crash reports' "Keys"
//      section under the snake_case wire form of the AllowedLogKey.
//   2. os_log (DEBUG builds, gated by #if DEBUG) — for Console.app visibility during
//      development.
//
// See architecture.md §14, /shared/canonical-names.md §2, Story 1.5 dev notes.

import Foundation
import os                            // for os_log — the ONLY allowed use in the app
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif

struct SafeLog {

    private static let logger = OSLog(subsystem: "com.xaeryx.translatorrep", category: "SafeLog")

    /// Log a single event with one allowed key + one value. The `value` is converted
    /// to its String form for both routes. Callers MUST NOT pass conversation content,
    /// PII, or Flores codes as `value` — enforced by code review (architecture §16).
    static func event(_ key: AllowedLogKey, _ value: Any) {
        let wireKey = key.rawValue
        let stringified = String(describing: value)

        #if DEBUG
        os_log("%{public}@=%{public}@", log: logger, type: .debug, wireKey, stringified)
        #endif

        #if canImport(FirebaseCrashlytics)
        // Crashlytics.crashlytics() is safe to call pre-FirebaseApp.configure() — it
        // returns a singleton that no-ops when Firebase isn't initialized (Story 1.4
        // wires the actual init).
        Crashlytics.crashlytics().setCustomValue(stringified, forKey: wireKey)
        #endif
    }
}
