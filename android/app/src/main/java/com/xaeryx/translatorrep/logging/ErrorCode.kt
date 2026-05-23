package com.xaeryx.translatorrep.logging

/**
 * Stable error-code registry mirroring /shared/error-codes.md §2 (which itself mirrors
 * architecture.md §10). Adding a new code requires an ADR amendment + simultaneous
 * Android + iOS PR — single-platform additions are rejected by code review.
 *
 * Usage pattern:
 *
 *     SafeLog.event(AllowedLogKey.ERROR_CODE, ErrorCode.ERR_TRANS_PROVIDER_UNAVAIL)
 *
 * Severities are encoded in the prefix:
 * - `ERR_*` — error; user-visible failure surface OR fatal (Call ends).
 * - `WARN_*` — warning; recoverable / soft-fail; UI surfaces a calm indicator.
 * - `INFO_*` — informational; expected state transitions worth logging.
 *
 * Cross-platform contract: every code string here MUST appear byte-identical in the
 * iOS [ErrorCode.swift] file.
 */
object ErrorCode {

    // ---- Errors (ERR_*) ----

    /** Translation provider returned an error / failed to respond. */
    const val ERR_TRANS_PROVIDER_UNAVAIL: String = "ERR_TRANS_PROVIDER_UNAVAIL"

    /** Translation provider exceeded the per-utterance timeout. */
    const val ERR_TRANS_PROVIDER_TIMEOUT: String = "ERR_TRANS_PROVIDER_TIMEOUT"

    /** On-device ASR engine initialization failed (fatal — surfaces as crash). */
    const val ERR_ASR_INIT_FAILED: String = "ERR_ASR_INIT_FAILED"

    /** Underlying WebRTC connection dropped; LiveKit will attempt auto-reconnect. */
    const val ERR_NETWORK_DROPPED: String = "ERR_NETWORK_DROPPED"

    /** E2EE ephemeral key exchange failed; the Call ends. */
    const val ERR_E2EE_KEY_EXCHANGE_FAILED: String = "ERR_E2EE_KEY_EXCHANGE_FAILED"

    /** LiveKit Room signaling or media layer failed unrecoverably; the Call ends. */
    const val ERR_LIVEKIT_ROOM_FAILED: String = "ERR_LIVEKIT_ROOM_FAILED"

    /** Entered pairing code is malformed or doesn't exist in /codes/{code}. */
    const val ERR_PAIRING_CODE_INVALID: String = "ERR_PAIRING_CODE_INVALID"

    /** Pairing code existed but has expired or been regenerated. */
    const val ERR_PAIRING_CODE_EXPIRED: String = "ERR_PAIRING_CODE_EXPIRED"

    // ---- Warnings (WARN_*) ----

    /** ASR returned a result below the confidence threshold; show inline tertiary marker. */
    const val WARN_ASR_LOW_CONFIDENCE: String = "WARN_ASR_LOW_CONFIDENCE"

    /** Peer hasn't yet published their ephemeral E2EE key; show one-time indicator. */
    const val WARN_E2EE_KEY_NOT_READY: String = "WARN_E2EE_KEY_NOT_READY"

    /** Remote video track stopped flowing; show VideoPausedTile, retry every 5s. */
    const val WARN_VIDEO_TRACK_SUSPENDED: String = "WARN_VIDEO_TRACK_SUSPENDED"

    // ---- Informational (INFO_*) ----

    /** Detected a Sundanese clause that was passed through as a placeholder. */
    const val INFO_SUNDANESE_PLACEHOLDER: String = "INFO_SUNDANESE_PLACEHOLDER"

    /** Translation model is loading; show "Preparing translator" one-time indicator. */
    const val INFO_MODEL_LOADING: String = "INFO_MODEL_LOADING"

    /** Partner has left the call; show CallWaitingForPartnerState banner. */
    const val INFO_WAITING_FOR_PARTNER: String = "INFO_WAITING_FOR_PARTNER"
}
