package com.xaeryx.translatorrep.call.signaling

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Stores this device's FCM registration token at `/users/{uid}.fcmToken` (Story 2.5). The
 * auth-proxy's `/v1/notify` reads it (server-side, admin SDK) to send the high-priority
 * incoming-call push. The owner writes their own `/users` doc — allowed by the deployed rules,
 * no rule change needed.
 */
class FcmTokenRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** Fetch the current FCM token and persist it. Call after sign-in. */
    suspend fun registerCurrent(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        saveToken(uid, token)
    }

    /** Persist a specific token (used from `onNewToken` when FCM rotates it). */
    suspend fun saveToken(uid: String, token: String) {
        firestore.collection(COLLECTION_USERS).document(uid)
            .set(mapOf(FIELD_FCM_TOKEN to token), SetOptions.merge())
            .await()
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val FIELD_FCM_TOKEN = "fcmToken"
    }
}
