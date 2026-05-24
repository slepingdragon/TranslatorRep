package com.xaeryx.translatorrep

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.xaeryx.translatorrep.firebase.FirebaseBootstrap

/**
 * Application class. Forces dark mode app-wide per UX-DR2 (Theme A is the
 * only theme in v1 baseline; Theme C custom-image background lands in
 * Epic 8). Material You dynamic color is never invoked anywhere in the
 * app — see `ui/theme/Theme.kt`.
 *
 * Firebase initialization (anonymous Auth + Firestore + App Check) is
 * activated here via [FirebaseBootstrap.init] (Story 1.4 Phase 1).
 */
class TranslatorRepApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Story 1.1 AC: setDefaultNightMode(MODE_NIGHT_YES) at
        // Application.onCreate(). Ensures dark mode regardless of system
        // theme; UX spec post-reconciliation has 2 themes (Dark + Image),
        // neither of which respects system light/dark setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Story 1.4 Phase 1: FirebaseApp.initializeApp + App Check provider
        // install (DebugAppCheckProviderFactory in debug builds via the
        // src/debug variant of AppCheckFactoryProvider; PlayIntegrityAppCheck
        // ProviderFactory in release via src/release). All errors are caught
        // + SafeLog'd; cold-boot is never blocked by Firebase init failure.
        FirebaseBootstrap.init(this)

        // TODO Story 1.8: signInAnonymously() before any UI renders.
    }
}
