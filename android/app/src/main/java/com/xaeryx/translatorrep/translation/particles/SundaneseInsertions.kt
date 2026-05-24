package com.xaeryx.translatorrep.translation.particles

/**
 * Sundanese lexical insertion detection (DR §4 / TQ-4).
 *
 * **STUB — Story 3.2b.** When a speaker switches into Sundanese mid-utterance,
 * this module detects the Sundanese span and signals [RenderMode.SUNDANESE_PLACEHOLDER]
 * to the caption renderer so the English side shows a quiet `[Sundanese]`
 * placeholder instead of garbled English (architecture §B + PRD FR-22).
 *
 * Implemented as a side-channel to [ParticleProcessor] rather than another
 * [ParticleRule] because Sundanese spans aren't single particles — they can be
 * full clauses — and the rendering semantics differ (placeholder, not equivalent).
 *
 * Story 3.2b will populate:
 *   - A token dictionary (≥12 lexical insertions per AC) of common Sundanese
 *     words/phrases speakers commonly drop into Indonesian conversation.
 *   - Span-detection (start..end indices) for the Sundanese portion of the source.
 *   - Integration with [ParticleProcessor.postProcess] so the resulting
 *     [PostProcessed.renderMode] is set to [RenderMode.SUNDANESE_PLACEHOLDER]
 *     when a Sundanese span is detected.
 */
internal object SundaneseInsertions {
    // TODO Story 3.2b: implement detection + token dictionary.
}
