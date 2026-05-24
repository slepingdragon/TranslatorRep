package com.xaeryx.translatorrep.call.callSession

import com.xaeryx.translatorrep.call.CallType
import kotlinx.coroutines.flow.Flow

/**
 * The fake-able seam that owns the underlying realtime room (architecture Patterns §13: UI
 * never calls the LiveKit SDK directly — only [CallSession] talks to a [RoomManager], and only
 * a `RoomManager` talks to LiveKit). Production impl is
 * [com.xaeryx.translatorrep.call.livekit.LiveKitRoomManager]; tests use an in-memory fake so
 * [CallSession]'s orchestration is unit-testable without the LiveKit SDK.
 */
interface RoomManager {

    /**
     * Connect to the room for [callType] and emit [RoomState] transitions until the call ends.
     * The full connect mechanics (auth-proxy JWT → `room.connect` → event mapping) land in
     * Story 2.3.
     */
    fun connect(callType: CallType): Flow<RoomState>

    /** Leave + tear down the room (Story 2.8 end-call / Epic 7 lifecycle). */
    suspend fun disconnect()
}
