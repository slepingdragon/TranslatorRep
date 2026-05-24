package com.xaeryx.translatorrep.call.callSession

import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.logging.AllowedLogKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

            val emitted = session.startCall(CallType.AUDIO).toList()

            assertEquals(CallType.AUDIO, manager.connectedWith)
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

    private class FakeRoomManager(private val states: Flow<RoomState>) : RoomManager {
        var connectedWith: CallType? = null
            private set
        var disconnected = false
            private set

        override fun connect(callType: CallType): Flow<RoomState> {
            connectedWith = callType
            return states
        }

        override suspend fun disconnect() {
            disconnected = true
        }
    }
}
