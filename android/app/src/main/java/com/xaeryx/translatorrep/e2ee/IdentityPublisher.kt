package com.xaeryx.translatorrep.e2ee

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * The fake-able seam for publishing the X25519 identity **public** key (Story 1.12). The only
 * key material that leaves the device. Implemented over Firestore `/users/{uid}.identityPub`.
 */
interface IdentityPublisher {
    /** Publish the 32-byte [publicKey] to the caller's own `/users/{uid}.identityPub`. */
    suspend fun publishIdentityPub(uid: String, publicKey: ByteArray)
}

/**
 * Firestore-backed [IdentityPublisher]. Writes the public key as a `bytes`/[Blob] field on the
 * caller's OWN `/users/{uid}` doc (owner-writable per the deployed rules — unlike the partner's
 * doc). Merge-write so it doesn't clobber `pairId` / `displayName`.
 */
class IdentityFirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : IdentityPublisher {

    override suspend fun publishIdentityPub(uid: String, publicKey: ByteArray) {
        firestore.collection(COLLECTION_USERS).document(uid)
            .set(mapOf(FIELD_IDENTITY_PUB to Blob.fromBytes(publicKey)), SetOptions.merge())
            .await()
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_IDENTITY_PUB = "identityPub"
    }
}
