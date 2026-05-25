package com.xaeryx.translatorrep.call.livekit

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

/** Result of a `POST /v1/token` call (Story 2.3, per shared/auth-proxy-api.md). */
sealed interface TokenResult {
    /** A LiveKit JWT + the WS URL + room name to connect with. */
    data class Success(val livekitJwt: String, val livekitWsUrl: String, val roomName: String) : TokenResult

    /** Failed — [errorCode] is an `ERR_*` from the proxy, or a client-side `ERR_TOKEN_*`. */
    data class Failure(val errorCode: String) : TokenResult
}

/**
 * Fetches a short-lived LiveKit JWT from the auth-proxy (Story 2.3). It (1) grabs the Firebase
 * **ID token** + **App Check token** on-device, (2) `POST`s them with `callType` + `peerUid` to
 * `{AUTH_PROXY_BASE_URL}/v1/token` (contract: shared/auth-proxy-api.md), (3) returns the
 * `livekitJwt` + `livekitWsUrl` + `roomName`. Network/SDK glue (no unit test — verified by
 * compile + on-device, like other SDK wrappers); [LiveKitRoomManager] uses it inside `connect`.
 */
class LiveKitTokenFetcher(
    private val baseUrl: String = BuildConfig.AUTH_PROXY_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val appCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
) {

    @Suppress("TooGenericExceptionCaught") // Network + Firebase token calls throw varied types.
    suspend fun fetchToken(callType: CallType, peerUid: String): TokenResult = withContext(Dispatchers.IO) {
        try {
            val idToken = auth.currentUser?.getIdToken(false)?.await()?.token
                ?: return@withContext TokenResult.Failure(ERR_NO_SESSION)
            val appCheckToken = appCheck.getAppCheckToken(false).await().token

            val bodyJson = JSONObject()
                .put("firebaseIdToken", idToken)
                .put("appCheckToken", appCheckToken)
                .put("callType", callType.wireName)
                .put("peerUid", peerUid)
                .toString()

            val request = Request.Builder()
                .url("$baseUrl/v1/token")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext TokenResult.Failure(errorCodeFrom(text, response.code))
                }
                val json = JSONObject(text)
                TokenResult.Success(
                    livekitJwt = json.getString("livekitJwt"),
                    livekitWsUrl = json.getString("livekitWsUrl"),
                    roomName = json.getString("roomName"),
                )
            }
        } catch (e: Exception) {
            TokenResult.Failure(ERR_TOKEN_FETCH)
        }
    }

    /** Pull the proxy's `{"error":"ERR_*"}` code, else synthesize from the HTTP status. */
    private fun errorCodeFrom(body: String, httpCode: Int): String =
        runCatching { JSONObject(body).getString("error") }.getOrNull() ?: "ERR_TOKEN_HTTP_$httpCode"

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val ERR_NO_SESSION = "ERR_TOKEN_NO_SESSION"
        const val ERR_TOKEN_FETCH = "ERR_TOKEN_FETCH"
    }
}
