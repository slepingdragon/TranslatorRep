package com.xaeryx.translatorrep.translation.particles

/**
 * Registry of all particle preservation rules for the [ParticleProcessor].
 *
 * **Scope of THIS file (Story 3.2b Phase 1):** 5 of 14 TQ-1 Indonesian
 * discourse particles fully implemented (`loh`, `kan`, `sih`, `dong`, `deh`).
 * Remaining 9 (`kok`, `ya`, `lah`, `kah`, `nih`, `tuh`, `mah`, `juga`, `also`)
 * queued as Story 3.2b Phase 2+. TQ-3/4/5/6/7/8 categories deferred entirely.
 * iOS Swift parity is Story 3.2c.
 *
 * Source of truth for rule semantics: Domain Research §1 (Indonesian
 * discourse particles) + §3 (Gen-Z slang) + §4 (Sundanese insertions) +
 * §6 (cultural-pragmatic). Cross-reference: `shared/particle-rules-fixtures/`.
 *
 * All five rules so far share the same shape — sentence-final discourse
 * particles with a comma-prefixed English equivalent. The
 * [sentenceFinalParticle] helper captures that shape so adding new entries
 * is a single line. New categories (slang word-substitution, Sundanese
 * spans, religious verbatim) will need different helpers.
 */
internal object ParticleRules {

    /**
     * Returns the rules applicable to a (sourceLang, targetLang) pair.
     * Currently filters by `sourceLang == "id-ID"` since v1 only supports
     * Indonesian-source rules. iOS parity (Story 3.2c) will share this
     * registry shape.
     */
    fun applicableRules(sourceLang: String, targetLang: String): List<ParticleRule> =
        allRules.filter { it.sourceLang == sourceLang && it.targetEquivalent(targetLang) != null }

    /**
     * All registered rules. Order matters: if two patterns could match the same
     * span, the first registered wins (relevant once TQ-8 slang rules land,
     * where short slang tokens could overlap with particle names).
     */
    private val allRules: List<ParticleRule> = listOf(
        // ── TQ-1 discourse particles, sentence-final position, comma-prefix English equivalent
        sentenceFinalParticle(name = "loh", englishEquivalent = ", you know"),
        sentenceFinalParticle(name = "kan", englishEquivalent = ", right?"),
        sentenceFinalParticle(name = "sih", englishEquivalent = ", though"),
        sentenceFinalParticle(name = "dong", englishEquivalent = ", please"),
        sentenceFinalParticle(name = "deh", englishEquivalent = ", then"),
        // Phase 2 additions (2026-05-24):
        sentenceFinalParticle(name = "kok", englishEquivalent = ", honestly"),
        sentenceFinalParticle(name = "ya", englishEquivalent = ", yeah?"),
        sentenceFinalParticle(name = "lah", englishEquivalent = ", obviously"),

        // TODO Story 3.2b Phase 3 — these need DIFFERENT shape from sentence-final helper:
        //   kah  — formal-question SUFFIX on words ("apakah"/"siapakah"/etc); strip in target,
        //          no equivalent inject. Detection is on a closed set of -kah formal words.
        //   nih  — proximal deictic, sentence-INITIAL or post-noun position, not sentence-final.
        //   tuh  — distal deictic, mirror of nih.
        //   mah  — mid-sentence concessive ("as for X, ..."), position-aware.
        //   juga — mid-sentence "also", position-variable.
        //   also — Indonesian loanword duplicating juga semantics; could share rule or alias.
    )

    /**
     * Helper for the common case: a sentence-final discourse particle whose
     * English equivalent is comma-prefixed and inserts just before any trailing
     * terminal punctuation (or at end of string if no terminal punct).
     *
     * Detect regex: `(?<=\s)<name>(?=[\s.,!?;:]*$)` — lookbehind for whitespace
     * so the marker substitution preserves the leading space; lookahead allows
     * optional terminal punctuation between the particle and end-of-string.
     *
     * Inject: places equivalent immediately before any trailing punctuation,
     * preserving that punctuation. With multi-punct cleanup in
     * [ParticleProcessor.postProcess], any `?` duplicated by a `, right?`
     * equivalent landing before a source `?` collapses to a single `?`.
     */
    private fun sentenceFinalParticle(
        name: String,
        englishEquivalent: String,
    ): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            // Build pattern with name interpolated as a literal — particle names
            // are lowercase alpha only (validated by convention), so no regex-escape
            // is strictly required, but defensively use `Regex.escape` if a future
            // particle name contains regex metacharacters.
            val pattern = """(?<=\s)${Regex.escape(name)}(?=[\s.,!?;:]*$)"""
            Regex(pattern = pattern, options = setOf(RegexOption.IGNORE_CASE))
                .find(text)
                ?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
        },
        targetEquivalents = mapOf("en-US" to englishEquivalent),
        inject = { current, equivalent, _ ->
            val trailingPunct = Regex("""[.,!?;:]+\s*$""").find(current)
            if (trailingPunct != null) {
                current.substring(0, trailingPunct.range.first) + equivalent + trailingPunct.value
            } else {
                current + equivalent
            }
        },
    )
}

/**
 * A single particle-preservation rule. Closed over the four operations the
 * [ParticleProcessor] needs:
 *
 *  - [name]: short identifier used in the `[PARTICLE:<name>]` marker.
 *  - [sourceLang]: BCP 47 code; only this rule's source language triggers detection.
 *  - [detect]: scan source text → optional [ParticleMatch].
 *  - [targetEquivalents]: map from target-language BCP 47 → equivalent string.
 *  - [inject]: insert the target equivalent into the rawTarget at the right position.
 */
internal data class ParticleRule(
    val name: String,
    val sourceLang: String,
    val detect: (String) -> ParticleMatch?,
    val targetEquivalents: Map<String, String>,
    val inject: (current: String, equivalent: String, sourceMatch: ParticleMatch) -> String,
) {
    fun targetEquivalent(targetLang: String): String? = targetEquivalents[targetLang]
}

/** A single particle hit within a source string. */
internal data class ParticleMatch(
    val startIndex: Int,
    val endIndex: Int,
    val matched: String,
)
