package com.xaeryx.translatorrep.call.livekit

import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.RoomManager
import com.xaeryx.translatorrep.call.callSession.RoomState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The only class that talks to the LiveKit Android SDK (architecture Patterns §13). Owned by
 * [com.xaeryx.translatorrep.call.callSession.CallSession]; the UI never references it.
 *
 * **Story 2.2 scaffold.** The real connection lands in **Story 2.3**:
 *  - [connect] will request a LiveKit JWT from the auth-proxy (Story 2.1; `callType` claim +
 *    peer UID), `room.connect(wsUrl, token)`, publish the local audio track, and map LiveKit
 *    `RoomEvent.Connected` / `ParticipantConnected` / `Disconnected` → [RoomState].
 *  - [disconnect] will `room.disconnect()` + release the engine.
 *
 * The SFU host is **LiveKit Cloud** (the Oracle path was dropped); the `wsUrl` + auth-proxy
 * base URL become config when Story 2.3 wires this. Until then [connect] emits nothing.
 */
class LiveKitRoomManager : RoomManager {

    override fun connect(callType: CallType): Flow<RoomState> {
        // TODO(Story 2.3): auth-proxy JWT → room.connect → map RoomEvent → RoomState.
        return emptyFlow()
    }

    override suspend fun disconnect() {
        // TODO(Story 2.3 / 2.8): room.disconnect() + release.
    }
}
