package com.xaeryx.translatorrep.call.callSession

/**
 * Lifecycle of the LiveKit room behind a Call (architecture Patterns §1 + ADR-A6). Emitted by
 * [CallSession.startCall]. [WAITING_FOR_PARTNER] is wired now but unused until Epic 7
 * (leave-and-rejoin). [wireName] is the SafeLog `room_state` value (architecture §"Firestore
 * Schema" naming).
 */
enum class RoomState(val wireName: String) {
    /** Connected to the room; media flowing. */
    ACTIVE("active"),

    /** One side left; the room is held open for the rejoin window (Epic 7). */
    WAITING_FOR_PARTNER("waitingForPartner"),

    /** The call is over; the room is torn down. */
    ENDED("ended"),
}
