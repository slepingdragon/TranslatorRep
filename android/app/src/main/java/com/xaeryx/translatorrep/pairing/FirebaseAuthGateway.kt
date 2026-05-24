package com.xaeryx.translatorrep.pairing

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * The narrow seam between [AnonymousAuthRepository] and the Firebase Auth SDK.
 *
 * It exists for one reason: [AnonymousAuthRepository] holds the Story 1.8 sign-in
 * logic (existing-session vs. fresh sign-in vs. failure), and that logic must be
 * unit-testable on the plain JVM. The project's test toolchain is JUnit 4 only — no
 * Robolectric, no MockK (see `android/gradle/libs.versions.toml`) — so the repository
 * cannot touch `FirebaseAuth` directly in a unit test. This interface is the fake-able
 * boundary; tests supply a hand-written fake, production supplies [FirebaseAuthGatewayImpl].
 *
 * Both members are intentionally minimal — Story 1.8 needs exactly "is there already a
 * session?" and "create an anonymous session". Sign-out / account-linking are not v1.
 */
interface FirebaseAuthGateway {

    /**
     * The UID of the currently-signed-in user, or `null` if there is no local session
     * (genuine first launch, or the user data was cleared). Synchronous + offline:
     * Firebase Auth persists the session locally, so a returning launch resolves the UID
     * here with no network call — this is what makes the UID survive app kills/restarts.
     */
    fun currentUid(): String?

    /**
     * Perform anonymous sign-in and return the new stable UID. Suspends until Firebase
     * resolves the `Task`. Throws (typically `FirebaseAuthException` / a network error)
     * if sign-in cannot complete — the caller maps that to [AuthState.Failed].
     */
    suspend fun signInAnonymously(): String
}

/**
 * Production [FirebaseAuthGateway] backed by the real [FirebaseAuth] singleton. Mirrors
 * the call shape already proven in `firebase/FirebaseSmokeTest.kt`
 * (`signInAnonymously().await()`), now on the production sign-in path.
 */
class FirebaseAuthGatewayImpl(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FirebaseAuthGateway {

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        return result.user?.uid
            ?: error("signInAnonymously() succeeded but returned no user")
    }
}
