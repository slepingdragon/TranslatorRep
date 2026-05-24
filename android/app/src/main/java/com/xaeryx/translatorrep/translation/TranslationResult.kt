package com.xaeryx.translatorrep.translation

/**
 * The outcome of a [com.xaeryx.translatorrep.translation.nmt.TranslationProvider.translate] call
 * (Story 3.3). Identical fields on iOS (`TranslationResult` struct) for cross-platform parity.
 *
 * @param targetText the translated text (post-processed when produced by the
 *   `RuleBasedTranslationProvider` decorator, Story 3.10).
 * @param particlesPreserved Indonesian particles the rules carried through (e.g. `["loh"]`) —
 *   surfaced for debug tracing (Story 3.11) + the regression corpus (Story 3.7).
 * @param status [TranslationStatus] — ok / failed / low-confidence.
 * @param renderMode [RenderMode] — e.g. `SUNDANESE_PLACEHOLDER` when a full Sundanese clause was
 *   detected (orthogonal to [status]; Patterns §7).
 * @param confidence model confidence in `0.0..1.0`.
 */
data class TranslationResult(
    val targetText: String,
    val particlesPreserved: List<String>,
    val status: TranslationStatus,
    val renderMode: RenderMode,
    val confidence: Double,
)
