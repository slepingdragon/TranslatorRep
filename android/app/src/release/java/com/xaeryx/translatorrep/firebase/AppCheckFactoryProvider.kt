package com.xaeryx.translatorrep.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release-variant App Check provider — uses Firebase's `PlayIntegrityAppCheckProviderFactory`.
 *
 * Requires:
 * - Google Play Services on the device.
 * - The app's package name + signing-key SHA-1 registered with Play Integrity in
 *   Google Play Console + linked to the Firebase project's Google Cloud project.
 *
 * Per [`firebase/appcheck/android-providers.md`](../../../../../../../../../firebase/appcheck/android-providers.md),
 * Play Integrity is in monitor-only mode initially. Flip enforcement ON via Firebase
 * console → App Check → APIs → Cloud Firestore → Enforce after at least 24h of
 * monitoring with zero unexpected rejections (avoids accidentally locking out real
 * users from a misconfiguration).
 *
 * Variant-specific source set: parallel to the debug variant at `src/debug/java/.../firebase/`.
 */
internal object AppCheckFactoryProvider {

    fun get(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()

    /** Short label written by [FirebaseBootstrap] into the `app_check_init` SafeLog event. */
    fun providerLabel(): String = "playintegrity"
}
