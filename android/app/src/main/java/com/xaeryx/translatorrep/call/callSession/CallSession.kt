package com.xaeryx.translatorrep.call.callSession

import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach

/**
 * The orchestration layer between the UI and the realtime/translation providers (architecture
 * Patterns §1 + §13). `CallSession` owns the [RoomManager] (LiveKit room lifecycle) and, in
 * later stories, the ASR/Translation flow and caption state — it is the single seam every
 * call-time feature (captions Epic 3/4, E2EE Epic 5, video Epic 6, leave-and-rejoin Epic 7)
 * plugs into. The UI observes [startCall]; it never touches LiveKit/providers directly.
 *
 * Story 2.2 establishes the seam; [startCall] delegates to [RoomManager.connect] (a scaffold
 * until Story 2.3 wires the real connection) and logs each [RoomState] transition.
 */
class CallSession(
    private val roomManager: RoomManager,
    private val logEvent: (AllowedLogKey, Any) -> Unit = SafeLog::event,
) {

    /** Mic mute state for the In-Call screen (Story 2.7). Each new call starts unmuted. */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /**
     * Start a Call of [callType] with the partner [peerUid] and observe its [RoomState] until it
     * ends. Returns the room's state stream (no separate "connecting" state — the In-Call screen
     * treats pre-[RoomState.ACTIVE] as connecting). Each transition is logged via
     * [AllowedLogKey.ROOM_STATE].
     */
    fun startCall(callType: CallType, peerUid: String): Flow<RoomState> {
        _muted.value = false // a fresh call always begins with the mic live
        return roomManager.connect(callType, peerUid).onEach { logEvent(AllowedLogKey.ROOM_STATE, it.wireName) }
    }

    /**
     * Toggle the microphone (Story 2.7). Disables the mic FIRST, then flips the exposed [muted]
     * state — so a failed SDK call doesn't leave the UI lying about a mic that's still live.
     * Returns the new muted state.
     */
    suspend fun toggleMute(): Boolean {
        val next = !_muted.value
        roomManager.setMicrophoneEnabled(!next)
        _muted.value = next
        return next
    }

    /** End the current Call (Story 2.8). */
    suspend fun endCall() = roomManager.disconnect()
}
