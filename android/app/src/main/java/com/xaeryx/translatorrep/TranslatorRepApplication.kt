package com.xaeryx.translatorrep

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application class. Forces dark mode app-wide per UX-DR2 (Theme A is the
 * only theme in v1 baseline; Theme C custom-image background lands in
 * Epic 8). Material You dynamic color is never invoked anywhere in the
 * app — see `ui/theme/Theme.kt`.
 *
 * Firebase initialization (anonymous Auth + Firestore + App Check) lands
 * in Story 1.4 once `google-services.json` is in place.
 */
class TranslatorRepApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Story 1.1 AC: setDefaultNightMode(MODE_NIGHT_YES) at
        // Application.onCreate(). Ensures dark mode regardless of system
        // theme; UX spec post-reconciliation has 2 themes (Dark + Image),
        // neither of which respects system light/dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // TODO Story 1.4: FirebaseApp.initializeApp(this) — gated on
        //                 google-services.json being present.
        // TODO Story 1.4: Firebase.initialize App Check (Play Integrity in
        //                 release, DebugAppCheckProviderFactory in debug).
        // TODO Story 1.4: CrashlyticsConfig.configure(this) — once Firebase is
        //                 initialized; see logging/CrashlyticsConfig.kt.
        // SafeLog (Story 1.5) needs no Application-startup wiring — it's a
        // top-level object that lazily reaches Crashlytics via runCatching, so
        // pre-Firebase-init calls are graceful no-ops on the Crashlytics route
        // (debug Log.d still emits). See logging/SafeLog.kt.
        // TODO Story 1.8: signInAnonymously() before any UI renders.
    }
}
