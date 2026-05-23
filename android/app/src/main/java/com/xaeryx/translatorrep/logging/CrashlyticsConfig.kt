package com.xaeryx.translatorrep.logging

import android.content.Context

/**
 * Forward-compatibility stub for Story 1.4. Once Firebase is initialized
 * (`FirebaseApp.initializeApp(context)` will be called from
 * [com.xaeryx.translatorrep.TranslatorRepApplication.onCreate] starting at Story 1.4),
 * this object will configure crash-free-session reporting defaults: turning Crashlytics
 * on/off based on the user's FR-30 privacy toggle (Story 8.8), setting the user ID
 * to the anonymous Firebase Auth UID, and registering session-start callbacks.
 *
 * Until Story 1.4 lands, this object is a no-op. [SafeLog.event] already routes to
 * Crashlytics via [com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance] —
 * wrapped in runCatching so the pre-init state is safe.
 *
 * See architecture.md §14 + Story 8.8 (Crashlytics Opt-In Toggle).
 */
object CrashlyticsConfig {

    /**
     * Configure Crashlytics defaults at app startup. Called from [TranslatorRepApplication.onCreate]
     * after [com.google.firebase.FirebaseApp.initializeApp] succeeds (Story 1.4 wires this).
     */
    @Suppress("UNUSED_PARAMETER")
    fun configure(context: Context) {
        // TODO Story 1.4: FirebaseCrashlytics.getInstance()
        //                     .setCrashlyticsCollectionEnabled(/* user toggle */)
        // TODO Story 8.8: respect Settings → Privacy → "Send anonymous crash reports" toggle
    }
}
