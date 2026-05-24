package com.xaeryx.translatorrep.translation.asr

/**
 * An event from an [AsrProvider]'s recognition stream (Story 3.3).
 *
 * A typical utterance emits zero-or-more [Partial]s (interim hypotheses; only when the provider
 * reports `supportsStreamingPartials`) followed by exactly one [Final], or an [Error]. The
 * `text` is recognized source-language text — it is conversation content and MUST NOT be logged
 * (architecture §14).
 */
sealed interface AsrEvent {

    /** An interim hypothesis that may still change. Emitted only by streaming providers. */
    data class Partial(val text: String) : AsrEvent

    /** A settled recognition result for an utterance. */
    data class Final(val text: String) : AsrEvent

    /**
     * Recognition failed. [errorCode] is a stable
     * [com.xaeryx.translatorrep.logging.ErrorCode] constant (e.g. `ERR_ASR_INIT_FAILED`) — safe
     * to log, unlike the recognized text.
     */
    data class Error(val errorCode: String) : AsrEvent
}
