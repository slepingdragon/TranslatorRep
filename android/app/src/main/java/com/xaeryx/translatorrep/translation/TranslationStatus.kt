package com.xaeryx.translatorrep.translation

/**
 * Outcome of a translation attempt. Canonical enum per
 * [shared/canonical-names.md §1] + architecture §7 (Failure-State Taxonomy).
 *
 * - [OK] — translation produced a target string above the confidence threshold.
 * - [FAILED] — translation failed terminally (model unreachable, OOM, etc.);
 *   caller renders the `TranslationFailureMarker` UX per Story 3.15 / 4.4.
 * - [LOW_CONFIDENCE] — translation produced output but model confidence is
 *   below the threshold; caller renders the low-confidence marker variant.
 *
 * Wire form: `ok` / `failed` / `lowConfidence`. Note `lowConfidence` is
 * camelCase per canonical-names.md (NOT `low_confidence`); this matches the
 * existing wire-form spec for enum variants with multi-word names.
 */
enum class TranslationStatus {
    OK,
    FAILED,
    LOW_CONFIDENCE,
}

/** Wire-form name per canonical-names.md §1. */
val TranslationStatus.wireValue: String
    get() = when (this) {
        TranslationStatus.OK -> "ok"
        TranslationStatus.FAILED -> "failed"
        TranslationStatus.LOW_CONFIDENCE -> "lowConfidence"
    }
