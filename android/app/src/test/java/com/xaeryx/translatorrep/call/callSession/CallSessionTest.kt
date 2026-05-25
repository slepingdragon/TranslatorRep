package com.xaeryx.translatorrep.call.callSession

import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.logging.AllowedLogKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CallSession] (Story 2.2) — the orchestration seam over a fake [RoomManager]
 * (no LiveKit SDK; JUnit4-only toolchain). Verifies `startCall` delegates to the manager,
 * surfaces its [RoomState] stream, and logs each transition; `endCall` delegates to disconnect.
 */
class CallSessionTest {

    @Test
    fun `startCall connects with the call type, emits room states, and logs each transition`() =
        runBlocking {
            val logged = mutableListOf<Pair<AllowedLogKey, Any>>()
            val manager = FakeRoomManager(flowOf(RoomState.ACTIVE, RoomState.ENDED))
            val session = CallSession(manager) { key, value -> logged += key to value }

            val emitted = session.startCall(CallType.AUDIO, PEER_UID).toList()

            assertEquals(CallType.AUDIO, manager.connectedWith)
            assertEquals(PEER_UID, manager.connectedPeerUid)
            assertEquals(listOf(RoomState.ACTIVE, RoomState.ENDED), emitted)
            assertEquals(
                listOf(
                    AllowedLogKey.ROOM_STATE to "active",
                    AllowedLogKey.ROOM_STATE to "ended",
                ),
                logged,
            )
        }

    @Test
    fun `endCall delegates to the room manager disconnect`() = runBlocking {
        val manager = FakeRoomManager(emptyFlow())
        val session = CallSession(manager) { _, _ -> }

        session.endCall()

        assertTrue(manager.disconnected)
    }

    @Test
    fun `toggleMute flips muted state and disables then re-enables the mic`() = runBlocking {
        val manager = FakeRoomManager(emptyFlow())
        val session = CallSession(manager) { _, _ -> }

        assertFalse(session.muted.value)

        assertTrue(session.toggleMute()) // returns the new state = muted
        assertTrue(session.muted.value)
        assertEquals(false, manager.micEnabled) // muted → mic disabled

        assertFalse(session.toggleMute()) // back to unmuted
        assertFalse(session.muted.value)
        assertEquals(true, manager.micEnabled) // unmuted → mic re-enabled
    }

    @Test
    fun `startCall resets mute so every call begins with a live mic`() = runBlocking {
        val manager = FakeRoomManager(emptyFlow())
        val session = CallSession(manager) { _, _ -> }

        session.toggleMute()
        assertTrue(session.muted.value)

        session.startCall(CallType.AUDIO, PEER_UID)

        assertFalse(session.muted.value)
    }

    private class FakeRoomManager(private val states: Flow<RoomState>) : RoomManager {
        var connectedWith: CallType? = null
            private set
        var connectedPeerUid: String? = null
            private set
        var disconnected = false
            private set
        var micEnabled: Boolean? = null
            private set

        override fun connect(callType: CallType, peerUid: String): Flow<RoomState> {
            connectedWith = callType
            connectedPeerUid = peerUid
            return states
        }

        override suspend fun disconnect() {
            disconnected = true
        }

        override suspend fun setMicrophoneEnabled(enabled: Boolean) {
            micEnabled = enabled
        }
    }

    private companion object {
        const val PEER_UID = "partner-uid"
    }
}
