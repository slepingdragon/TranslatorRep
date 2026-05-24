package com.xaeryx.translatorrep.pairing

import com.xaeryx.translatorrep.ids.UlidGenerator

/** Outcome of attempting to pair with a partner's code (Story 1.10, FR-3). */
sealed interface PairResult {
    /** Paired — `/pairs/{pairId}` created and the caller's own `pairId` written. */
    data class Success(val pairId: String) : PairResult

    /** No `/codes/{code}` doc → inline "Code not found". */
    data object NotFound : PairResult

    /** The code is the caller's own → inline "That's your own code" (never invalidates it). */
    data object OwnCode : PairResult

    /** The code's `expiresAt` is in the past → inline "Code expired". */
    data object Expired : PairResult
}

/**
 * Pairing logic (Story 1.10, FR-3): resolve a partner's 6-digit code, validate it, and — on a
 * valid code — create the `/pairs/{pairId}` relationship and write the caller's OWN
 * `/users/{uid}.pairId`. Pure suspend over the fake-able [CodeStore] + [PairStore] seams, with
 * the pair-id factory + clock injected, so [PairingCoordinatorTest] covers every branch without
 * Firestore. [PairingViewModel] is the thin `viewModelScope` wrapper.
 *
 * **Rules-compatible propagation (confirmed with Bania 2026-05-24):** only the initiator's side
 * is written here — the deployed rules forbid writing the partner's `/users` doc. The partner
 * discovers the pair via a `/pairs`-membership listener (Story 1.11).
 */
class PairingCoordinator(
    private val codeStore: CodeStore,
    private val pairStore: PairStore,
    private val pairIdFactory: () -> String = UlidGenerator::next,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Attempt to pair [myUid] with the owner of [enteredCode]. Validation order: not-found →
     * own-code → expired (own-code wins over expired so a user never sees "expired" for their
     * own code). On success, creates `/pairs/{pairId}` (`memberA` = caller) and writes the
     * caller's own `pairId`. Throws only on an unexpected store failure (the ViewModel maps
     * that to a generic retryable error); the validation outcomes are returned, not thrown.
     */
    suspend fun pair(myUid: String, enteredCode: String): PairResult {
        val record = codeStore.lookup(enteredCode) ?: return PairResult.NotFound
        if (record.ownerId == myUid) return PairResult.OwnCode
        if (record.expiresAtMillis != null && record.expiresAtMillis < now()) return PairResult.Expired

        val pairId = pairIdFactory()
        pairStore.createPair(pairId, memberA = myUid, memberB = record.ownerId)
        pairStore.setUserPairId(myUid, pairId)
        return PairResult.Success(pairId)
    }
}
