package com.xaeryx.translatorrep.call

/**
 * Audio output route for an in-call session (Story 2.9 / ADR-A5, FR-28). The In-Call control row
 * cycles through whichever of these are currently available; [BLUETOOTH] / [WIRED_HEADSET] only
 * appear while such a device is connected (auto-selected at call start, with fallback to
 * [EARPIECE] if it disconnects mid-call).
 */
enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }
