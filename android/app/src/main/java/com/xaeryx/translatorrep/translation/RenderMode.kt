package com.xaeryx.translatorrep.translation

/**
 * How a `Caption` should be rendered by the UI. Canonical enum per
 * [shared/canonical-names.md §1] + architecture §F.
 *
 * - [DEFAULT] — standard caption row with source + target text.
 * - [SUNDANESE_PLACEHOLDER] — quiet `[Sundanese]` placeholder on the English
 *   side when the speaker code-switched into Sundanese mid-utterance. Per
 *   UX-DR and PRD FR-22, full Sundanese clauses don't translate to English
 *   (model coverage gap) and rendering them as garbled English is worse than
 *   showing an honest "Sundanese was spoken here" marker.
 *
 * Wire form (snake_case for Data Channel + Firestore): `default` /
 * `sundanesePlaceholder`. See `wireValue` extension below — kept aligned
 * with the existing [AllowedLogKey.wireKey] pattern.
 */
enum class RenderMode {
    DEFAULT,
    SUNDANESE_PLACEHOLDER,
}

/** Wire-form snake_case name (camelCase variant for `sundanesePlaceholder` to match canonical-names.md §1). */
val RenderMode.wireValue: String
    get() = when (this) {
        RenderMode.DEFAULT -> "default"
        RenderMode.SUNDANESE_PLACEHOLDER -> "sundanesePlaceholder"
    }
