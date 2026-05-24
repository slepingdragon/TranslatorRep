package com.xaeryx.translatorrep.pairing

import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the app's anonymous-authentication state (Story 1.8, FR-1). Single instance held by
 * [com.xaeryx.translatorrep.TranslatorRepApplication] and shared with every screen — sign-in
 * is established before any UI renders, so an Application-scoped holder (not a screen-scoped
 * ViewModel) is the correct home for it. The pairing ViewModels (Story 1.9+) read [state] /
 * the resolved UID from here rather than touching Firebase Auth themselves.
 *
 * State is exposed as a [StateFlow]<[AuthState]> per architecture §"State Management"
 * (StateFlow on Android, MutableStateFlow kept private).
 *
 * Logging is injected ([logEvent]) so the sign-in logic stays pure-JVM unit-testable; in
 * production it routes to [SafeLog.event]. Only privacy-safe values are logged: the
 * [AllowedLogKey.AUTH_UID] event carries the first [UID_LOG_PREFIX_LENGTH] characters of the
 * UID (never the full UID) on success, or a `failed:<ExceptionClass>` label on failure —
 * matching the convention established in `firebase/FirebaseSmokeTest.kt`.
 */
class AnonymousAuthRepository(
    private val gateway: FirebaseAuthGateway,
    private val logEvent: (AllowedLogKey, Any) -> Unit = SafeLog::event,
) {

    private val _state = MutableStateFlow<AuthState>(AuthState.SigningIn)

    /** App-wide anonymous-auth state. Starts at [AuthState.SigningIn]. */
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * Establish a stable anonymous UID. Safe to call repeatedly:
     *
     * - If already [AuthState.SignedIn], returns immediately (idempotent).
     * - If a local Firebase session already exists ([FirebaseAuthGateway.currentUid] is
     *   non-null), resolves to [AuthState.SignedIn] with NO network call — the
     *   persist-across-restart path.
     * - Otherwise performs anonymous sign-in; success → [AuthState.SignedIn], any failure →
     *   [AuthState.Failed] (never throws — a cold-boot network failure must not crash the app;
     *   the UI offers retry, which simply calls this again).
     *
     * Called once from `Application.onCreate` on an application scope, and again from the
     * MainActivity retry affordance (only reachable from [AuthState.Failed], so the two call
     * sites never overlap).
     */
    @Suppress("TooGenericExceptionCaught") // Firebase sign-in throws checked + unchecked types; map all to Failed.
    suspend fun ensureSignedIn() {
        if (_state.value is AuthState.SignedIn) return

        gateway.currentUid()?.let { existingUid ->
            _state.value = AuthState.SignedIn(existingUid)
            logEvent(AllowedLogKey.AUTH_UID, existingUid.take(UID_LOG_PREFIX_LENGTH))
            return
        }

        _state.value = AuthState.SigningIn
        try {
            val uid = gateway.signInAnonymously()
            _state.value = AuthState.SignedIn(uid)
            logEvent(AllowedLogKey.AUTH_UID, uid.take(UID_LOG_PREFIX_LENGTH))
        } catch (e: Exception) {
            val reason = e.javaClass.simpleName
            _state.value = AuthState.Failed(reason)
            logEvent(AllowedLogKey.AUTH_UID, "failed:$reason")
        }
    }

    private companion object {
        /** Privacy: log only the first 4 UID chars, never the full UID (architecture §14). */
        const val UID_LOG_PREFIX_LENGTH = 4
    }
}
