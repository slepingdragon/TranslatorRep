package com.xaeryx.translatorrep.translation.particles

/**
 * Registry of all particle preservation rules for the [ParticleProcessor].
 *
 * **Scope of THIS file (Story 3.2 Android-only):** Only the TQ-1 Indonesian
 * discourse particle `loh` is fully implemented. The remaining 13 TQ-1
 * particles (`kan`, `sih`, `dong`, `deh`, `kok`, `ya`, `lah`, `kah`, `nih`,
 * `tuh`, `mah`, `juga`, `also`) are queued as Story 3.2b. The structural
 * pattern is clear: each particle is one [ParticleRule] entry below. New
 * particles drop in without changing [ParticleProcessor].
 *
 * Source of truth for rule semantics: Domain Research §1 (Indonesian
 * discourse particles) + §3 (Gen-Z slang) + §4 (Sundanese insertions) +
 * §6 (cultural-pragmatic). Cross-reference: `shared/particle-rules-fixtures/`.
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
     * All registered rules. Order matters slightly: longer-match particles
     * should appear before substrings of themselves to avoid greedy mismatch.
     * For TQ-1 alone there's no overlap; revisit when TQ-8 slang lands.
     */
    private val allRules: List<ParticleRule> = listOf(
        // ── TQ-1: `loh` (sentence-final, gentle insistence + emotional weight)
        ParticleRule(
            name = "loh",
            sourceLang = "id-ID",
            // Detect a sentence-final "loh" preceded by whitespace and optionally
            // followed by terminal punctuation. The lookbehind `(?<=\s)` keeps the
            // preceding space OUT of the match so substitution preserves it
            // (otherwise marker insertion would butt up against the previous word).
            detect = { text ->
                val match = Regex(
                    pattern = """(?<=\s)loh(?=[\s.,!?;:]*$)""",
                    options = setOf(RegexOption.IGNORE_CASE),
                ).find(text)
                match?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
            },
            targetEquivalents = mapOf(
                "en-US" to ", you know",
            ),
            // For loh: inject the equivalent JUST BEFORE the trailing punctuation
            // (or at the end if no punctuation). E.g.: "I miss you." + ", you know"
            // → "I miss you, you know."
            inject = { current, equivalent, _ ->
                val trailingPunct = Regex("""[.,!?;:]+\s*$""").find(current)
                if (trailingPunct != null) {
                    current.substring(0, trailingPunct.range.first) + equivalent + trailingPunct.value
                } else {
                    current + equivalent
                }
            },
        ),

        // TODO Story 3.2b: kan (tag-question / "right?")
        // TODO Story 3.2b: sih (mild contrast / "though")
        // TODO Story 3.2b: dong (affectionate command / "please")
        // TODO Story 3.2b: deh (acceptance / "okay then")
        // TODO Story 3.2b: kok (mild surprise / "huh")
        // TODO Story 3.2b: ya (confirmation / "yes/right")
        // TODO Story 3.2b: lah (emphasis / "indeed")
        // TODO Story 3.2b: kah (formal question marker — usually drop in target)
        // TODO Story 3.2b: nih (proximal "this here")
        // TODO Story 3.2b: tuh (distal "that there")
        // TODO Story 3.2b: mah (concession / "as for")
        // TODO Story 3.2b: juga (also)
        // TODO Story 3.2b: also (loanword; same semantics as juga)
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
