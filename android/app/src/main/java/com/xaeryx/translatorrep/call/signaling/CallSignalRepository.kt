package com.xaeryx.translatorrep.call.signaling

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.IncomingCall
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * In-app incoming-call signaling over the shared `/pairs/{pairId}` doc (Story 2.5/2.6 — in-app
 * MVP). The caller writes an `incomingCall` field; the partner's snapshot listener surfaces it as
 * an [IncomingCall]. We reuse the pair doc because the deployed `firestore.rules` already let
 * either member read/update it — so no new collection or rules are needed.
 *
 * The caller is filtered out of their own ring (a member must not "receive" the call they placed).
 * This works while the app is in the foreground; full lock-screen delivery (FCM high-priority +
 * ConnectionService) is the Story 2.5/2.6 upgrade.
 */
class CallSignalRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val pairs get() = firestore.collection(COLLECTION_PAIRS)

    /** Live incoming-call signal for [myUid] on [pairId], or `null` (incl. our own ring). */
    fun observeIncomingCall(pairId: String, myUid: String): Flow<IncomingCall?> = callbackFlow {
        val registration = pairs.document(pairId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                SafeLog.event(AllowedLogKey.ERROR_CODE, "incoming_call_listen_${error.code.name}")
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val signal = snapshot?.get(FIELD_INCOMING_CALL) as? Map<String, Any?>
            val caller = signal?.get(FIELD_CALLER) as? String
            val type = (signal?.get(FIELD_CALL_TYPE) as? String)?.let(::callTypeOf)
            val declined = signal?.get(FIELD_DECLINED) as? Boolean == true
            // Ignore our own ring, and a ring we've already declined (dismisses the screen).
            val incoming = if (caller != null && caller != myUid && type != null && !declined) {
                IncomingCall(callerUid = caller, callType = type)
            } else {
                null
            }
            trySend(incoming)
        }
        awaitClose { registration.remove() }
    }

    /** Ring the partner: stamp the `incomingCall` field on the pair doc (merge, member-allowed). */
    suspend fun ring(pairId: String, callerUid: String, callType: CallType) {
        pairs.document(pairId).set(
            mapOf(
                FIELD_INCOMING_CALL to mapOf(
                    FIELD_CALLER to callerUid,
                    FIELD_CALL_TYPE to callType.wireName,
                ),
            ),
            SetOptions.merge(),
        ).await()
    }

    /** Clear the ring (on accept / hang-up / end). Member-allowed field delete. */
    suspend fun clear(pairId: String) {
        pairs.document(pairId).update(FIELD_INCOMING_CALL, FieldValue.delete()).await()
    }

    /**
     * Decline an incoming ring: mark `incomingCall.declined = true` (keeps caller/type). The
     * callee's own [observeIncomingCall] then returns `null` (screen dismisses) and the caller's
     * [observeOutgoingDeclined] fires so their call ends promptly (rather than ringing out).
     */
    suspend fun decline(pairId: String) {
        pairs.document(pairId).update("$FIELD_INCOMING_CALL.$FIELD_DECLINED", true).await()
    }

    /** True when MY outgoing ring ([myUid] is the caller) has been declined by the partner. */
    fun observeOutgoingDeclined(pairId: String, myUid: String): Flow<Boolean> = callbackFlow {
        val registration = pairs.document(pairId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                SafeLog.event(AllowedLogKey.ERROR_CODE, "decline_listen_${error.code.name}")
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val signal = snapshot?.get(FIELD_INCOMING_CALL) as? Map<String, Any?>
            val caller = signal?.get(FIELD_CALLER) as? String
            val declined = signal?.get(FIELD_DECLINED) as? Boolean == true
            trySend(caller == myUid && declined)
        }
        awaitClose { registration.remove() }
    }

    private fun callTypeOf(wire: String): CallType? = CallType.entries.firstOrNull { it.wireName == wire }

    private companion object {
        const val COLLECTION_PAIRS = "pairs"
        const val FIELD_INCOMING_CALL = "incomingCall"
        const val FIELD_CALLER = "caller"
        const val FIELD_CALL_TYPE = "callType"
        const val FIELD_DECLINED = "declined"
    }
}
