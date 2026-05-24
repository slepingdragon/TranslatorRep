package com.xaeryx.translatorrep

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.xaeryx.translatorrep.firebase.FirebaseBootstrap
import com.xaeryx.translatorrep.pairing.AnonymousAuthRepository
import com.xaeryx.translatorrep.pairing.FirebaseAuthGatewayImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class. Forces dark mode app-wide per UX-DR2 (Theme A is the
 * only theme in v1 baseline; Theme C custom-image background lands in
 * Epic 8). Material You dynamic color is never invoked anywhere in the
 * app — see `ui/theme/Theme.kt`.
 *
 * Firebase initialization (anonymous Auth + Firestore + App Check) is
 * activated here via [FirebaseBootstrap.init] (Story 1.4 Phase 1), and the
 * anonymous sign-in itself is kicked off here (Story 1.8) so a stable UID is
 * being established before [MainActivity] renders its first frame.
 *
 * No dependency-injection framework is used (architecture decision: manual
 * construction for a 2-user app). [authRepository] is the app-wide auth singleton;
 * Activities read it via `(application as TranslatorRepApplication).authRepository`.
 */
class TranslatorRepApplication : Application() {

    /**
     * Application-lifetime scope for fire-and-forget startup work (anonymous sign-in).
     * [SupervisorJob] so one failed child never cancels the scope; [Dispatchers.Default]
     * because the work only suspends on Firebase + mutates a thread-safe StateFlow (no UI).
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** App-wide anonymous-auth state holder (Story 1.8). Shared with all screens. */
    val authRepository: AnonymousAuthRepository by lazy {
        AnonymousAuthRepository(FirebaseAuthGatewayImpl())
    }

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

        // Story 1.8: kick off anonymous sign-in immediately at cold boot so the
        // UID is (usually) ready by the time MainActivity composes — FR-1 wants a
        // stable UID within ~3s and no login UI. ensureSignedIn() never throws;
        // a first-boot network failure lands in AuthState.Failed and MainActivity
        // shows a retry affordance.
        appScope.launch { authRepository.ensureSignedIn() }
    }
}
