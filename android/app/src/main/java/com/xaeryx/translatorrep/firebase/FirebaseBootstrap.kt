package com.xaeryx.translatorrep.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog

/**
 * Initializes Firebase services for the app.
 *
 * Story 1.4 contract:
 * 1. `FirebaseApp.initializeApp(context)` — requires `android/app/google-services.json`
 *    (placed by Bania during Phase 0; gitignored).
 * 2. Install App Check provider via [AppCheckFactoryProvider] — debug variant uses
 *    `DebugAppCheckProviderFactory`; release variant uses `PlayIntegrityAppCheckProviderFactory`.
 *    Variant selection is by Gradle source-set discrimination, not BuildConfig branch — this
 *    means `firebase-appcheck-debug` (debugImplementation only) is never referenced by the
 *    release build's bytecode, so release compilation doesn't fail with NoClassDefFoundError.
 * 3. Anonymous sign-in is OUT of scope for [init] — that's Story 1.8. A one-shot smoke
 *    test of sign-in + Firestore write is in [FirebaseSmokeTest.runOnce].
 *
 * **Activation status (as of feature/1-4-firebase-android):** This object COMPILES but is
 * NOT called from production code yet. [TranslatorRepApplication.onCreate] has a commented-out
 * call-site (see TODOs in that file). Activation requires:
 *   - Bania completes Phase 0 of [docs/runbooks/firebase-setup-android.md]
 *   - A follow-up dev session uncomments the two Firebase plugins in `app/build.gradle.kts`
 *   - That same session uncomments the `FirebaseBootstrap.init(this)` line in Application.onCreate
 *
 * All logging goes through [SafeLog] per architecture §14 (the ForbiddenImport detekt rule
 * bans direct `android.util.Log` outside `logging/SafeLog.kt`).
 */
object FirebaseBootstrap {

    /**
     * Initialize Firebase + App Check. Idempotent — safe to call multiple times, but in
     * practice should be called exactly once from [Application.onCreate].
     *
     * Failures are caught and SafeLog'd rather than thrown — a logging-init failure must
     * not crash the app cold-boot. The most common pre-flight failure is missing
     * `google-services.json` (caught by the `google-services` Gradle plugin at build time,
     * not here; but defense-in-depth catch covers the rare runtime case).
     */
    @Suppress("TooGenericExceptionCaught")
    fun init(context: Context) {
        try {
            FirebaseApp.initializeApp(context)
            SafeLog.event(AllowedLogKey.FIREBASE_INIT, "success")
        } catch (e: RuntimeException) {
            SafeLog.event(
                AllowedLogKey.FIREBASE_INIT,
                "failed:${e.javaClass.simpleName}",
            )
            // Don't try to install App Check if FirebaseApp itself failed.
            return
        }

        try {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(AppCheckFactoryProvider.get())
            SafeLog.event(AllowedLogKey.APP_CHECK_INIT, AppCheckFactoryProvider.providerLabel())
        } catch (e: RuntimeException) {
            SafeLog.event(
                AllowedLogKey.APP_CHECK_INIT,
                "failed:${e.javaClass.simpleName}",
            )
        }
    }
}
