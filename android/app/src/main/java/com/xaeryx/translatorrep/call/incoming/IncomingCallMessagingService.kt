package com.xaeryx.translatorrep.call.incoming

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.xaeryx.translatorrep.call.signaling.FcmTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM messages (Story 2.5). Two jobs:
 *  - [onNewToken]: persist the rotated token to `/users/{uid}.fcmToken` (if signed in) so the
 *    proxy can still reach this device.
 *  - [onMessageReceived]: on a data-only `incoming_call` push, post the full-screen ring
 *    notification (works in the background — data-only messages always invoke this).
 *
 * The push is a "wake + ring" trigger only; the actual call state resolves from the `/pairs`
 * signal once [com.xaeryx.translatorrep.MainActivity] is foregrounded.
 */
class IncomingCallMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch { runCatching { FcmTokenRepository().saveToken(uid, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data[KEY_TYPE] == TYPE_INCOMING_CALL) {
            IncomingCallNotifier.showIncomingCall(applicationContext)
        }
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val TYPE_INCOMING_CALL = "incoming_call"
    }
}
