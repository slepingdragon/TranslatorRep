package com.xaeryx.translatorrep.call

/**
 * The kind of media exchange a Call carries (architecture Patterns §1). v1 places **audio**
 * calls (Epic 2); [VIDEO] exists for Epic 6 (the two-button `CallTypeSelector`, FR-26/ADR-A3).
 * The value rides the LiveKit JWT metadata + the incoming-call push payload as `call_type`.
 */
enum class CallType(val wireName: String) {
    AUDIO("audio"),
    VIDEO("video"),
}
