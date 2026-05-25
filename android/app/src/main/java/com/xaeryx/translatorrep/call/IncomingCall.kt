package com.xaeryx.translatorrep.call

/**
 * An incoming Call signalled by the partner (Story 2.5/2.6, in-app MVP). Surfaced to the callee
 * via [com.xaeryx.translatorrep.call.signaling.CallSignalRepository]; the caller never sees their
 * own ring.
 */
data class IncomingCall(val callerUid: String, val callType: CallType)
