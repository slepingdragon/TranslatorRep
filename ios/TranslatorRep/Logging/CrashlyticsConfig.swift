// CrashlyticsConfig.swift
// TranslatorRep — Story 1.5
//
// Forward-compatibility stub for Story 1.4. Once Firebase is initialized
// (`FirebaseApp.configure()` will be called from TranslatorRepApp.init() starting at
// Story 1.4), this enum will configure crash-free-session reporting defaults:
// turning Crashlytics on/off based on the user's FR-30 privacy toggle (Story 8.8),
// setting the user ID to the anonymous Firebase Auth UID, and registering
// session-start callbacks.
//
// Until Story 1.4 lands, configure() is a no-op. SafeLog.event already routes to
// Crashlytics — Crashlytics.crashlytics() is safe to call pre-init, so the
// pre-Firebase-init state is graceful.
//
// See architecture.md §14 + Story 8.8 (Crashlytics Opt-In Toggle).

import Foundation

enum CrashlyticsConfig {

    /// Configure Crashlytics defaults at app startup. Called from TranslatorRepApp.init()
    /// after FirebaseApp.configure() succeeds (Story 1.4 wires this).
    static func configure() {
        // TODO Story 1.4: Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(/* user toggle */)
        // TODO Story 8.8: respect Settings → Privacy → "Send anonymous crash reports" toggle
    }
}
