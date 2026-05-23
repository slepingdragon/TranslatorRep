// ErrorCode.swift
// TranslatorRep — Story 1.5
//
// Stable error-code registry mirroring /shared/error-codes.md §2 (which itself
// mirrors architecture.md §10). Adding a new code requires an ADR amendment plus a
// simultaneous Android + iOS PR — single-platform additions are rejected by review.
//
// Usage pattern:
//
//     SafeLog.event(.errorCode, ErrorCode.errTransProviderUnavail)
//
// Severities are encoded in the prefix:
//   - ERR_* — error; user-visible failure surface OR fatal (Call ends).
//   - WARN_* — warning; recoverable / soft-fail; UI surfaces a calm indicator.
//   - INFO_* — informational; expected state transitions worth logging.
//
// Cross-platform contract: every code string here MUST appear byte-identical in
// the Android ErrorCode.kt file.

import Foundation

enum ErrorCode {

    // MARK: - Errors (ERR_*)

    /// Translation provider returned an error / failed to respond.
    static let errTransProviderUnavail = "ERR_TRANS_PROVIDER_UNAVAIL"

    /// Translation provider exceeded the per-utterance timeout.
    static let errTransProviderTimeout = "ERR_TRANS_PROVIDER_TIMEOUT"

    /// On-device ASR engine initialization failed (fatal — surfaces as crash).
    static let errAsrInitFailed = "ERR_ASR_INIT_FAILED"

    /// Underlying WebRTC connection dropped; LiveKit will attempt auto-reconnect.
    static let errNetworkDropped = "ERR_NETWORK_DROPPED"

    /// E2EE ephemeral key exchange failed; the Call ends.
    static let errE2eeKeyExchangeFailed = "ERR_E2EE_KEY_EXCHANGE_FAILED"

    /// LiveKit Room signaling or media layer failed unrecoverably; the Call ends.
    static let errLivekitRoomFailed = "ERR_LIVEKIT_ROOM_FAILED"

    /// Entered pairing code is malformed or doesn't exist in /codes/{code}.
    static let errPairingCodeInvalid = "ERR_PAIRING_CODE_INVALID"

    /// Pairing code existed but has expired or been regenerated.
    static let errPairingCodeExpired = "ERR_PAIRING_CODE_EXPIRED"

    // MARK: - Warnings (WARN_*)

    /// ASR returned a result below the confidence threshold; show inline tertiary marker.
    static let warnAsrLowConfidence = "WARN_ASR_LOW_CONFIDENCE"

    /// Peer hasn't yet published their ephemeral E2EE key; show one-time indicator.
    static let warnE2eeKeyNotReady = "WARN_E2EE_KEY_NOT_READY"

    /// Remote video track stopped flowing; show VideoPausedTile, retry every 5s.
    static let warnVideoTrackSuspended = "WARN_VIDEO_TRACK_SUSPENDED"

    // MARK: - Informational (INFO_*)

    /// Detected a Sundanese clause that was passed through as a placeholder.
    static let infoSundanesePlaceholder = "INFO_SUNDANESE_PLACEHOLDER"

    /// Translation model is loading; show "Preparing translator" one-time indicator.
    static let infoModelLoading = "INFO_MODEL_LOADING"

    /// Partner has left the call; show CallWaitingForPartnerState banner.
    static let infoWaitingForPartner = "INFO_WAITING_FOR_PARTNER"
}
