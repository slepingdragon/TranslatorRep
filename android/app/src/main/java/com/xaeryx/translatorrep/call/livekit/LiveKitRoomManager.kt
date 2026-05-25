package com.xaeryx.translatorrep.call.livekit

import android.content.Context
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.RoomManager
import com.xaeryx.translatorrep.call.callSession.RoomState
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * The only class that talks to the LiveKit Android SDK (architecture Patterns §13). Owned by
 * [com.xaeryx.translatorrep.call.callSession.CallSession]; the UI never references it.
 *
 * Story 2.3 wires the real connection: fetch a JWT from the auth-proxy
 * ([LiveKitTokenFetcher]) → `room.connect(wsUrl, jwt)` → publish the local mic → stay in the
 * call until the collecting flow is cancelled (user ends), which disconnects the room. (E2EE
 * Insertable Streams is Epic 5; per-call media is DTLS-SRTP until then. Rich room-event
 * handling — peer-left, network drop, leave-and-rejoin — arrives with Epic 7.)
 */
class LiveKitRoomManager(
    private val appContext: Context,
    private val tokenFetcher: LiveKitTokenFetcher = LiveKitTokenFetcher(),
) : RoomManager {

    @Volatile
    private var room: Room? = null

    override fun connect(callType: CallType, peerUid: String): Flow<RoomState> = flow {
        when (val token = tokenFetcher.fetchToken(callType, peerUid)) {
            is TokenResult.Failure -> emit(RoomState.ENDED) // couldn't get a token → no call
            is TokenResult.Success -> emitCall(token)
        }
    }.onCompletion {
        // Flow cancelled (user left the call) or completed → tear down the room.
        room?.disconnect()
        room = null
    }

    /** Connect + publish mic, then hold [RoomState.ACTIVE] until the flow is cancelled. */
    @Suppress("TooGenericExceptionCaught") // LiveKit connect throws varied types; map to ENDED.
    private suspend fun FlowCollector<RoomState>.emitCall(token: TokenResult.Success) {
        val activeRoom = LiveKit.create(appContext)
        room = activeRoom
        val connected = try {
            activeRoom.connect(token.livekitWsUrl, token.livekitJwt)
            activeRoom.localParticipant.setMicrophoneEnabled(true)
            true
        } catch (e: CancellationException) {
            throw e // normal teardown — let it propagate to onCompletion
        } catch (e: Exception) {
            false
        }
        if (connected) {
            emit(RoomState.ACTIVE)
            awaitCancellation() // stay in the call until the collector is cancelled (End)
        } else {
            emit(RoomState.ENDED)
        }
    }

    override suspend fun disconnect() {
        room?.disconnect()
        room = null
    }
}
