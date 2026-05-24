package com.xaeryx.translatorrep.translation.particles

import com.xaeryx.translatorrep.translation.RenderMode
import com.xaeryx.translatorrep.translation.TranslationStatus

/**
 * Two-pass particle-preservation processor sitting BETWEEN the speaker's source
 * utterance and the raw NMT model — architecture pattern §11 + DR §1/§3/§4/§6.
 *
 * **Pass 1 — [preProcess]:** Identifies particle/slang/honorific/etc. tokens in
 * the source text and inserts markers (e.g. `[PARTICLE:loh]`) at their position
 * so the downstream NMT model is encouraged to preserve them through translation.
 * Returns [ProcessedText] containing the tagged text + the list of detected
 * particle names.
 *
 * **Pass 2 — [postProcess]:** Takes the NMT's raw output and the ORIGINAL
 * (un-tagged) source, re-detects particles from source, and either replaces
 * preserved markers in `rawTarget` with their target-language equivalents OR
 * injects equivalents at sensible positions if the NMT dropped the markers
 * (which most NMTs do at least some of the time). Idempotent — calling
 * [postProcess] on an already-post-processed string returns it unchanged.
 *
 * **Scope of THIS file (Story 3.2 Android-only):** Only the [ParticleRules.allRules]
 * registry of TQ-1 Indonesian discourse particles is functional, and only the
 * `loh` rule within that. The 5 sibling files
 * ([SundaneseInsertions], [HonorificStripping], [IndirectRefusals], [GenZSlang],
 * [ReligiousExpressions]) are stubs. Sub-letter follow-up stories (3.2b, 3.2c…)
 * fill the remaining rule tables. iOS Swift parity is Story 3.2c (Mac/iOS session).
 */
object ParticleProcessor {

    /** Marker format injected by [preProcess] and consumed by [postProcess]. */
    private val MARKER_REGEX: Regex = Regex("""\[PARTICLE:([a-z]+)]""")

    /**
     * Pre-process source text by tagging particles with `[PARTICLE:<name>]` markers.
     * Returns the tagged text + the list of particle names found (in source order).
     */
    fun preProcess(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): ProcessedText {
        var current = text
        val detected = mutableListOf<String>()
        for (rule in ParticleRules.applicableRules(sourceLang, targetLang)) {
            val match = rule.detect(current)
            if (match != null) {
                current = current.substring(0, match.startIndex) +
                    "[PARTICLE:${rule.name}]" +
                    current.substring(match.endIndex)
                detected.add(rule.name)
            }
        }
        return ProcessedText(text = current, particles = detected)
    }

    /**
     * Post-process the NMT's raw output back into a user-facing target string.
     *
     * Strategy (in order):
     * 1. If `rawTarget` already contains `[PARTICLE:<name>]` markers (NMT
     *    preserved them), replace each marker with that rule's target-language
     *    equivalent.
     * 2. For particles detected in `originalSource` whose markers did NOT
     *    appear in `rawTarget`, inject the target equivalent at an appropriate
     *    position (rule-defined; default = end of string).
     * 3. Skip injection if the equivalent is already present in `rawTarget`
     *    (idempotency — calling this on an already-post-processed string is a
     *    no-op for that particle).
     */
    fun postProcess(
        rawTarget: String,
        originalSource: String,
        sourceLang: String,
        targetLang: String,
    ): PostProcessed {
        var current = rawTarget
        val preserved = mutableListOf<String>()
        val rules = ParticleRules.applicableRules(sourceLang, targetLang)

        // Pass 1: substitute any markers the NMT preserved.
        val markerMatches = MARKER_REGEX.findAll(current).toList().reversed()
        for (match in markerMatches) {
            val particleName = match.groupValues[1]
            val rule = rules.firstOrNull { it.name == particleName }
            val equivalent = rule?.targetEquivalent(targetLang)
            if (equivalent != null) {
                current = current.substring(0, match.range.first) +
                    equivalent +
                    current.substring(match.range.last + 1)
                preserved.add(particleName)
            } else {
                // Unknown marker — strip it silently rather than leaking to UI.
                current = current.substring(0, match.range.first) +
                    current.substring(match.range.last + 1)
            }
        }

        // Pass 2: re-detect particles from source; inject any equivalent that
        // isn't already in `current`. Nested-if form (rather than guard +
        // continue) keeps detekt's LoopWithTooManyJumpStatements rule happy.
        for (rule in rules) {
            val alreadyPreserved = preserved.contains(rule.name)
            if (!alreadyPreserved) {
                val sourceMatch = rule.detect(originalSource)
                val equivalent = rule.targetEquivalent(targetLang)
                if (sourceMatch != null && equivalent != null) {
                    if (current.contains(equivalent)) {
                        // Idempotency: equivalent already present (this postProcess
                        // call is a no-op for this particle).
                        preserved.add(rule.name)
                    } else {
                        current = rule.inject(current, equivalent, sourceMatch)
                        preserved.add(rule.name)
                    }
                }
            }
        }

        // Tidy:
        //   1. Collapse runs of whitespace (marker substitution can leave doubles).
        //   2. Pull punctuation tight to its preceding word (the loh equivalent
        //      ", you know" inserted after " [PARTICLE:loh]" would otherwise leave
        //      a stray space before the comma).
        //   3. Trim ends.
        current = current.replace(Regex("\\s+"), " ")
        current = current.replace(Regex("""\s+([.,!?;:])"""), "$1")
        current = current.trim()

        return PostProcessed(
            text = current,
            particlesPreserved = preserved,
            renderMode = RenderMode.DEFAULT,
            translationStatus = TranslationStatus.OK,
        )
    }
}

/** Output of [ParticleProcessor.preProcess]. */
data class ProcessedText(
    val text: String,
    val particles: List<String>,
)

/** Output of [ParticleProcessor.postProcess]. */
data class PostProcessed(
    val text: String,
    val particlesPreserved: List<String>,
    val renderMode: RenderMode,
    val translationStatus: TranslationStatus,
)
