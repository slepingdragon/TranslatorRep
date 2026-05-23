package com.xaeryx.translatorrep.firebase

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-variant App Check provider — uses Firebase's `DebugAppCheckProviderFactory`.
 *
 * The debug provider exists because real Play Integrity attestation fails on emulators
 * without Play Services + on rooted devices + on sideloaded debug builds. To use a
 * debug build against real Firebase backends, register the debug token (logged to
 * Logcat by the factory on first run) in the Firebase console under App Check →
 * your Android app → ⚙ → Manage debug tokens. See
 * [`docs/runbooks/firebase-setup-android.md`](../../../../../../../../../docs/runbooks/firebase-setup-android.md) §5d.
 *
 * Variant-specific source set: this file is ONLY compiled when building the debug
 * variant. The release variant's parallel file at `src/release/java/.../firebase/`
 * returns the Play Integrity factory. This pattern avoids referencing
 * `DebugAppCheckProviderFactory` (which is `debugImplementation` only) from any
 * release-variant code path.
 */
internal object AppCheckFactoryProvider {

    fun get(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()

    /** Short label written by [FirebaseBootstrap] into the `app_check_init` SafeLog event. */
    fun providerLabel(): String = "debug"
}
