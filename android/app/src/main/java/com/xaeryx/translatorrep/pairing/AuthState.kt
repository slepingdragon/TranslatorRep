package com.xaeryx.translatorrep.pairing

/**
 * App-wide anonymous-authentication state, established on first launch (Story 1.8,
 * FR-1) and consumed by every screen that needs the signed-in UID (the pairing arc,
 * Stories 1.9–1.13, then Calls). Exposed as a [kotlinx.coroutines.flow.StateFlow] by
 * [AnonymousAuthRepository] per architecture §"State Management" (StateFlow on Android).
 *
 * This is deliberately NOT a Firebase type — the rest of the app depends on this sealed
 * surface, never on `FirebaseUser`, so the auth backend stays swappable and the consumers
 * stay unit-testable on the JVM.
 *
 * There is intentionally no `SignedOut` case: v1 has no logout (FR-1 is silent, permanent
 * anonymous auth). [Failed] is a transient first-launch error (e.g. no network on a cold
 * first boot) that the UI offers to retry — it is not a logged-out state.
 */
sealed interface AuthState {

    /**
     * Sign-in is in flight (or about to start). The UI shows a branded loading surface —
     * never a login/signup form (FR-1: no account UI is ever shown). This is also the
     * initial state before [AnonymousAuthRepository.ensureSignedIn] runs.
     */
    data object SigningIn : AuthState

    /**
     * A stable anonymous UID is established. On a returning launch this is reached
     * immediately from the locally-cached Firebase session (no network round-trip),
     * satisfying the "UID persists across app kills/restarts" acceptance criterion.
     */
    data class SignedIn(val uid: String) : AuthState

    /**
     * First-launch sign-in failed (no cached session AND the sign-in call threw — almost
     * always no connectivity on first boot). [reason] is a non-PII diagnostic label (an
     * exception class name), safe to surface in UI copy indirectly and to log via SafeLog.
     */
    data class Failed(val reason: String) : AuthState
}
