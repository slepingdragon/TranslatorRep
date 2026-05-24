package com.xaeryx.translatorrep.pairing

import com.xaeryx.translatorrep.logging.AllowedLogKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AnonymousAuthRepository] — the Story 1.8 anonymous-sign-in core (FR-1).
 *
 * No Robolectric / MockK in the toolchain (JUnit 4 only), so the Firebase boundary is a
 * hand-written [FakeAuthGateway] and the sign-in logic is driven with [runBlocking]. State
 * is read synchronously from the [kotlinx.coroutines.flow.StateFlow]'s `.value` after the
 * suspend call returns. Logging is captured via the injected `logEvent` lambda, keeping the
 * real `SafeLog` (and thus `android.util.Log`) entirely off the JVM test path.
 */
class AnonymousAuthRepositoryTest {

    private val logged = mutableListOf<Pair<AllowedLogKey, Any>>()

    private fun repository(gateway: FakeAuthGateway) =
        AnonymousAuthRepository(gateway) { key, value -> logged += key to value }

    @Test
    fun `initial state is SigningIn before ensureSignedIn runs`() {
        val repo = repository(FakeAuthGateway())
        assertEquals(AuthState.SigningIn, repo.state.value)
    }

    @Test
    fun `no existing session - signs in anonymously and reaches SignedIn`() = runBlocking {
        val gateway = FakeAuthGateway(existingUid = null, signInResult = Result.success(NEW_UID))
        val repo = repository(gateway)

        repo.ensureSignedIn()

        assertEquals(AuthState.SignedIn(NEW_UID), repo.state.value)
        assertEquals("anonymous sign-in should be called exactly once", 1, gateway.signInCallCount)
    }

    @Test
    fun `existing session - reaches SignedIn without calling sign-in (persists across restart)`() =
        runBlocking {
            val gateway = FakeAuthGateway(existingUid = CACHED_UID)
            val repo = repository(gateway)

            repo.ensureSignedIn()

            assertEquals(AuthState.SignedIn(CACHED_UID), repo.state.value)
            assertEquals(
                "a cached Firebase session must NOT trigger a network sign-in",
                0,
                gateway.signInCallCount,
            )
        }

    @Test
    fun `sign-in failure - reaches Failed with the exception class name and never throws`() =
        runBlocking {
            val gateway = FakeAuthGateway(
                existingUid = null,
                signInResult = Result.failure(IllegalStateException("offline")),
            )
            val repo = repository(gateway)

            repo.ensureSignedIn()

            assertEquals(AuthState.Failed("IllegalStateException"), repo.state.value)
        }

    @Test
    fun `retry after failure - a subsequent ensureSignedIn succeeds once connectivity returns`() =
        runBlocking {
            val gateway = FakeAuthGateway(
                existingUid = null,
                signInResult = Result.failure(IllegalStateException("offline")),
            )
            val repo = repository(gateway)

            repo.ensureSignedIn()
            assertTrue(repo.state.value is AuthState.Failed)

            // Connectivity returns; the retry path re-attempts sign-in.
            gateway.signInResult = Result.success(NEW_UID)
            repo.ensureSignedIn()

            assertEquals(AuthState.SignedIn(NEW_UID), repo.state.value)
            assertEquals(2, gateway.signInCallCount)
        }

    @Test
    fun `already SignedIn - ensureSignedIn is idempotent and does not re-sign-in`() = runBlocking {
        val gateway = FakeAuthGateway(existingUid = null, signInResult = Result.success(NEW_UID))
        val repo = repository(gateway)

        repo.ensureSignedIn()
        repo.ensureSignedIn()

        assertEquals(AuthState.SignedIn(NEW_UID), repo.state.value)
        assertEquals("second call should be a no-op", 1, gateway.signInCallCount)
    }

    @Test
    fun `logs only the first 4 UID chars, never the full UID (privacy)`() = runBlocking {
        val gateway = FakeAuthGateway(existingUid = null, signInResult = Result.success(LONG_UID))
        val repo = repository(gateway)

        repo.ensureSignedIn()

        val authEvents = logged.filter { it.first == AllowedLogKey.AUTH_UID }
        assertEquals("exactly one AUTH_UID event expected", 1, authEvents.size)
        assertEquals(LONG_UID.take(UID_PREFIX_LEN), authEvents.single().second)
        assertFalse(
            "the full UID must never be logged",
            logged.any { it.second == LONG_UID },
        )
    }

    @Test
    fun `failure is logged as failed-prefixed reason, not a raw UID`() = runBlocking {
        val gateway = FakeAuthGateway(
            existingUid = null,
            signInResult = Result.failure(IllegalStateException("offline")),
        )
        val repo = repository(gateway)

        repo.ensureSignedIn()

        val authEvent = logged.single { it.first == AllowedLogKey.AUTH_UID }
        assertEquals("failed:IllegalStateException", authEvent.second)
    }

    /** Hand-written [FirebaseAuthGateway] fake — no MockK needed. */
    private class FakeAuthGateway(
        private val existingUid: String? = null,
        var signInResult: Result<String> = Result.success(NEW_UID),
    ) : FirebaseAuthGateway {
        var signInCallCount = 0
            private set

        override fun currentUid(): String? = existingUid

        override suspend fun signInAnonymously(): String {
            signInCallCount++
            return signInResult.getOrThrow()
        }
    }

    private companion object {
        const val NEW_UID = "new-uid-abcdef"
        const val CACHED_UID = "cached-uid-123456"
        const val LONG_UID = "abcdef0123456789ZZZZ"
        const val UID_PREFIX_LEN = 4
    }
}
