package com.xaeryx.translatorrep.pairing

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.SetOptions
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** A resolved `/codes/{code}` document — what a pairing lookup needs (Story 1.10). */
data class CodeRecord(
    val ownerId: String,
    /** Epoch millis of `expiresAt`, or `null` if the doc predates / omits the field. */
    val expiresAtMillis: Long?,
)

/**
 * The fake-able seam between the pairing logic and Firestore's `/codes/{code}` collection.
 * Mirrors the [FirebaseAuthGateway] pattern from Story 1.8: the allocation / collision /
 * reuse logic ([PairingCodeAllocator]) and the pairing logic ([PairingCoordinator]) are
 * unit-tested against in-memory fakes, never the live SDK (the toolchain is JUnit4-only —
 * no Robolectric / MockK).
 */
interface CodeStore {

    /**
     * The caller's existing pairing code (the doc id under `/codes` whose `ownerId` equals
     * [ownerId]), or `null` if they don't have one yet. Lets a returning user keep the SAME
     * code their partner may already be holding (FR-2: valid until used or regenerated)
     * instead of minting a new one every launch.
     */
    suspend fun findOwnedCode(ownerId: String): String?

    /** Whether `/codes/{code}` already exists — the collision check at generation time. */
    suspend fun exists(code: String): Boolean

    /** Resolve `/codes/{code}` to its owner + expiry, or `null` if it doesn't exist (Story 1.10). */
    suspend fun lookup(code: String): CodeRecord?

    /** Create `/codes/{code}` owned by [ownerId]. The impl stamps `createdAt` + `expiresAt`. */
    suspend fun create(code: String, ownerId: String)

    /** Delete `/codes/{code}` — used when regenerating (invalidates the prior code). */
    suspend fun delete(code: String)
}

/**
 * The fake-able seam for writing the pairing relationship (Story 1.10): the `/pairs/{pairId}`
 * doc plus the caller's OWN `/users/{uid}.pairId`.
 *
 * **Deliberately no partner-side write.** The deployed `firestore.rules` allow a client to
 * write only its own `/users/{uid}` doc, so the pairing initiator cannot set the partner's
 * `pairId`. The partner instead discovers the pair via a `/pairs`-membership listener and
 * writes its own `pairId` (Story 1.11). This is the rules-compatible reading of the epic AC
 * ("both users' pairId updated") — confirmed with Bania 2026-05-24.
 */
interface PairStore {

    /** Create `/pairs/{pairId}` = `{memberA, memberB, createdAt}`. Caller must be [memberA]. */
    suspend fun createPair(pairId: String, memberA: String, memberB: String)

    /** Merge `pairId` into the caller's OWN `/users/{uid}` doc. */
    suspend fun setUserPairId(uid: String, pairId: String)
}

/**
 * Firestore-backed [CodeStore] + [PairStore] for the `/codes`, `/pairs`, and `/users`
 * collections (Stories 1.9 + 1.10).
 *
 * **Field name note:** the code-owner field is **`ownerId`**, matching the DEPLOYED security
 * rules (`firebase/firestore.rules` — `request.resource.data.ownerId == request.auth.uid`).
 * The epic/architecture text says `ownerUid`; the deployed rules are the runtime source of
 * truth (writing `ownerUid` would be rejected). Confirmed with Bania 2026-05-24: keep `ownerId`.
 *
 * `expiresAt` is set [CODE_VALIDITY_DAYS] out. v1's UX treats a code as valid until used or
 * explicitly regenerated (UX spec §Open-items), so this is a generous cleanup/TTL hook rather
 * than a short hard expiry; Story 1.10's "Code expired" branch reads it.
 */
class PairingFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CodeStore, PairStore, PairDirectory {

    private val codes get() = firestore.collection(COLLECTION_CODES)
    private val pairs get() = firestore.collection(COLLECTION_PAIRS)
    private val users get() = firestore.collection(COLLECTION_USERS)

    override suspend fun findOwnedCode(ownerId: String): String? {
        val snapshot = codes.whereEqualTo(FIELD_OWNER_ID, ownerId).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.id
    }

    override suspend fun exists(code: String): Boolean =
        codes.document(code).get().await().exists()

    override suspend fun lookup(code: String): CodeRecord? {
        val doc = codes.document(code).get().await()
        if (!doc.exists()) return null
        val ownerId = doc.getString(FIELD_OWNER_ID) ?: return null
        return CodeRecord(ownerId = ownerId, expiresAtMillis = doc.getTimestamp(FIELD_EXPIRES_AT)?.toDate()?.time)
    }

    override suspend fun create(code: String, ownerId: String) {
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + CODE_VALIDITY_MILLIS))
        codes.document(code).set(
            mapOf(
                FIELD_OWNER_ID to ownerId,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_EXPIRES_AT to expiresAt,
            ),
        ).await()
    }

    override suspend fun delete(code: String) {
        codes.document(code).delete().await()
    }

    override suspend fun createPair(pairId: String, memberA: String, memberB: String) {
        pairs.document(pairId).set(
            mapOf(
                FIELD_MEMBER_A to memberA,
                FIELD_MEMBER_B to memberB,
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun setUserPairId(uid: String, pairId: String) {
        users.document(uid).set(mapOf(FIELD_PAIR_ID to pairId), SetOptions.merge()).await()
    }

    // ── PairDirectory (Story 1.11) ──────────────────────────────────────────

    private fun membershipQuery(myUid: String) =
        pairs.where(Filter.or(Filter.equalTo(FIELD_MEMBER_A, myUid), Filter.equalTo(FIELD_MEMBER_B, myUid)))
            .limit(1)

    override fun observePairFor(myUid: String): Flow<RemotePair?> = callbackFlow {
        val registration = membershipQuery(myUid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Don't crash/hang — keep the last status (offline-first). Log the Firestore
                // error code (PERMISSION_DENIED / FAILED_PRECONDITION=missing-index /
                // UNAVAILABLE / …) so a stuck listener is diagnosable in Logcat.
                SafeLog.event(AllowedLogKey.ERROR_CODE, "pairs_listen_${error.code.name}")
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.firstOrNull()?.toRemotePair())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun findPairFor(myUid: String): RemotePair? =
        membershipQuery(myUid).get().await().documents.firstOrNull()?.toRemotePair()

    override suspend fun ensureOwnPairId(myUid: String, pairId: String) = setUserPairId(myUid, pairId)

    override suspend fun deletePair(pairId: String) {
        pairs.document(pairId).delete().await()
    }

    override suspend fun clearOwnPairId(myUid: String) {
        users.document(myUid).update(FIELD_PAIR_ID, FieldValue.delete()).await()
    }

    private fun DocumentSnapshot.toRemotePair(): RemotePair? {
        val a = getString(FIELD_MEMBER_A) ?: return null
        val b = getString(FIELD_MEMBER_B) ?: return null
        return RemotePair(pairId = id, memberA = a, memberB = b)
    }

    private companion object {
        const val COLLECTION_CODES = "codes"
        const val COLLECTION_PAIRS = "pairs"
        const val COLLECTION_USERS = "users"

        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_MEMBER_A = "memberA"
        const val FIELD_MEMBER_B = "memberB"
        const val FIELD_PAIR_ID = "pairId"

        const val CODE_VALIDITY_DAYS = 365L
        const val CODE_VALIDITY_MILLIS = CODE_VALIDITY_DAYS * 24L * 60L * 60L * 1000L
    }
}
