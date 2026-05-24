package com.xaeryx.translatorrep.pairing

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * The fake-able seam between [PairingCodeAllocator] and Firestore's `/codes/{code}`
 * collection. Mirrors the [FirebaseAuthGateway] pattern from Story 1.8: the allocation /
 * collision / reuse logic is unit-tested against an in-memory fake, never the live SDK
 * (the toolchain is JUnit4-only — no Robolectric / MockK).
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

    /** Create `/codes/{code}` owned by [ownerId]. The impl stamps `createdAt` + `expiresAt`. */
    suspend fun create(code: String, ownerId: String)

    /** Delete `/codes/{code}` — used when regenerating (invalidates the prior code). */
    suspend fun delete(code: String)
}

/**
 * Firestore-backed [CodeStore] for the `/codes/{code}` collection (Story 1.9, FR-2).
 *
 * **Field name note:** the doc owner field is **`ownerId`**, matching the DEPLOYED security
 * rules (`firebase/firestore.rules` — `request.resource.data.ownerId == request.auth.uid`).
 * The epic/architecture text says `ownerUid`; the deployed rules are the runtime source of
 * truth, and writing `ownerUid` would be rejected with PERMISSION_DENIED. (Drift noted in the
 * Story 1.9 file.)
 *
 * `expiresAt` is set to [CODE_VALIDITY_DAYS] out. v1's UX treats a code as valid until it is
 * used or explicitly regenerated (UX spec §Open-items), so this is a generous cleanup/TTL
 * hook rather than a short hard expiry; Story 1.10 reads it for its "Code expired" branch.
 */
class PairingFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CodeStore {

    private val codes get() = firestore.collection(COLLECTION_CODES)

    override suspend fun findOwnedCode(ownerId: String): String? {
        val snapshot = codes.whereEqualTo(FIELD_OWNER_ID, ownerId).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.id
    }

    override suspend fun exists(code: String): Boolean =
        codes.document(code).get().await().exists()

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

    private companion object {
        const val COLLECTION_CODES = "codes"
        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_EXPIRES_AT = "expiresAt"

        const val CODE_VALIDITY_DAYS = 365L
        const val CODE_VALIDITY_MILLIS = CODE_VALIDITY_DAYS * 24L * 60L * 60L * 1000L
    }
}
