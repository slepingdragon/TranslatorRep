package com.xaeryx.translatorrep.pairing

import kotlinx.coroutines.flow.Flow

/** A `/pairs/{pairId}` document as seen by a member (Story 1.11). */
data class RemotePair(val pairId: String, val memberA: String, val memberB: String) {
    /** The OTHER member's uid relative to [myUid]. */
    fun partnerOf(myUid: String): String = if (memberA == myUid) memberB else memberA
}

/**
 * The fake-able seam for reading the pairing relationship from Firestore `/pairs` (Story 1.11).
 * The partner-side discovery half of the rules-compatible pairing model (Story 1.10 wrote only
 * the initiator's side): a member finds/observes the `/pairs` doc they belong to, then writes
 * its OWN `/users/{uid}.pairId` for consistency. Mirrors the [FirebaseAuthGateway] /
 * [CodeStore] seam pattern so [PairingStatusRepository]'s reconcile logic is unit-testable
 * without the live SDK (JUnit4-only toolchain).
 */
interface PairDirectory {

    /**
     * Live view of the caller's pair, or `null` when not paired. Backed by a Firestore
     * snapshot listener — Firestore's offline cache serves the last-known value (and echoes a
     * just-created local pair) so this also drives the immediate post-pair transition and
     * offline-degraded launches.
     */
    fun observePairFor(myUid: String): Flow<RemotePair?>

    /** One-shot read of the caller's pair (e.g. a manual refresh). */
    suspend fun findPairFor(myUid: String): RemotePair?

    /**
     * Ensure the caller's OWN `/users/{uid}.pairId` is set (the partner does this on first
     * discovery; the initiator already did it in Story 1.10). Idempotent merge.
     */
    suspend fun ensureOwnPairId(myUid: String, pairId: String)
}
