package com.xaeryx.translatorrep.call.signaling

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.xaeryx.translatorrep.BuildConfig
import com.xaeryx.translatorrep.call.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Best-effort caller-side push trigger (Story 2.5): `POST {AUTH_PROXY_BASE_URL}/v1/notify` so the
 * partner's device rings even when backgrounded. Same auth as the token request (Firebase ID +
 * App Check). Entirely best-effort — any failure is swallowed; the in-app foreground ring (the
 * `/pairs` signal) is the source of truth, this just wakes a backgrounded peer.
 */
class NotifyClient(
    private val baseUrl: String = BuildConfig.AUTH_PROXY_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val appCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
) {
    @Suppress("TooGenericExceptionCaught") // Network + Firebase token calls throw varied types.
    suspend fun ringPartner(callType: CallType, peerUid: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token ?: return@runCatching
            val appCheckToken = runCatching { appCheck.getAppCheckToken(false).await().token }.getOrNull().orEmpty()
            val bodyJson = JSONObject()
                .put("firebaseIdToken", idToken)
                .put("appCheckToken", appCheckToken)
                .put("callType", callType.wireName)
                .put("peerUid", peerUid)
                .toString()
            val request = Request.Builder()
                .url("$baseUrl/v1/notify")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { /* fire-and-forget; ignore the result */ }
        }
        Unit
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
