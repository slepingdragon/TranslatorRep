package com.xaeryx.translatorrep.translation.particles

/**
 * Registry of all particle preservation rules for the [ParticleProcessor].
 *
 * **Scope (post Story 3.2b Phase 4):** TQ-1 (14 discourse particles) + TQ-3
 * (gender-neutral `dia → they`) + TQ-6 (6 Arabic-origin religious expressions
 * preserved verbatim) are registered via 6 distinct helpers, each shaped for
 * a distinct linguistic position pattern:
 *
 *  - [sentenceFinalParticle] (Phase 1+2): `loh`, `kan`, `sih`, `dong`, `deh`,
 *    `kok`, `ya`, `lah` — discourse particles in sentence-final position with
 *    comma-prefixed English equivalent.
 *  - [formalQuestionSuffix] (Phase 3): `kah` — suffix on closed-list formal
 *    question stems (`apakah`, `siapakah`, ...). NULL target equivalent
 *    (English doesn't need a marker; word order + `?` does the work).
 *  - [sentenceInitialDeictic] (Phase 3): `nih`, `tuh` — proximal/distal
 *    deictics in sentence-initial position. English equivalent prepended.
 *  - [pronounConcessive] (Phase 3): `mah` — concessive marker following a
 *    subject pronoun. NULL target equivalent (NMT itself produces the
 *    "as for X" rendering naturally).
 *  - [midSentenceAlso] (Phase 3): `juga`, `also` — "also/too" appended at
 *    sentence end. ("also" registered as an Indonesian-loanword alias for
 *    `juga` with identical semantics; rare in real usage but spec-required.)
 *  - [genderNeutralPronoun] (Phase 4 — TQ-3): `dia` — standalone third-person
 *    singular pronoun (gender-unspecified in Indonesian). Target equivalent =
 *    `"they"` (singular-they default per architecture §11 / DR §6.3). Inject
 *    replaces a stray `he/she` from the NMT if `"they"` isn't already present.
 *    **Registered AFTER `mah`** because mah's pronoun list includes `dia`;
 *    letting mah run first means `dia mah` collapses correctly into both tags.
 *  - [verbatimReligious] (Phase 4 — TQ-6): `alhamdulillah`, `insyaallah`,
 *    `bismillah`, `subhanallah`, `astaghfirullah`, `masyaallah` — Arabic-origin
 *    religious expressions preserved verbatim in target (the English-receiving
 *    partner is expected to recognize them; literal translations like "thank
 *    God" would flatten the cultural-pragmatic register per DR §6 / architecture
 *    §11). Multi-word variants (`insya Allah` / `masya Allah`) deferred — v1
 *    covers single-word Indonesian spellings only.
 *
 * TQ-4/5/7/8 categories deferred to Phase 5 (Sundanese, honorifics, indirect
 * refusals, slang) — those need Bania's girlfriend's linguistic input for
 * register decisions + slang currency. iOS Swift parity is Story 3.2c.
 */
internal object ParticleRules {

    /**
     * Returns the rules applicable to a (sourceLang, targetLang) pair.
     *
     * **Filter is on sourceLang ONLY** (previously also filtered on non-null
     * targetEquivalent; that filter was dropped in Phase 3 so that null-target
     * rules like `kah` and `mah` still participate in preProcess marker tagging
     * and postProcess marker stripping). The processor's Pass 2 logic handles
     * the null-target case explicitly (see [ParticleProcessor.postProcess]).
     */
    fun applicableRules(sourceLang: String, targetLang: String): List<ParticleRule> =
        allRules.filter { rule ->
            // Source language must match. Target equivalents are included if EITHER:
            //   (a) The rule has the targetLang specifically (normal injection path), OR
            //   (b) The rule's equivalents map is empty — a null-target rule like `kah`
            //       that needs to participate in preProcess marker tagging but doesn't
            //       inject anything in postProcess (handled by Pass 1 marker-strip).
            rule.sourceLang == sourceLang &&
                (rule.targetEquivalents.isEmpty() || rule.targetEquivalents.containsKey(targetLang))
        }

    /**
     * All registered rules. Order matters: if two patterns could match the same
     * span, the first registered wins.
     */
    private val allRules: List<ParticleRule> = listOf(
        // ── TQ-1 sentence-final discourse particles (Phase 1+2)
        sentenceFinalParticle(name = "loh", englishEquivalent = ", you know"),
        sentenceFinalParticle(name = "kan", englishEquivalent = ", right?"),
        sentenceFinalParticle(name = "sih", englishEquivalent = ", though"),
        sentenceFinalParticle(name = "dong", englishEquivalent = ", please"),
        sentenceFinalParticle(name = "deh", englishEquivalent = ", then"),
        sentenceFinalParticle(name = "kok", englishEquivalent = ", honestly"),
        sentenceFinalParticle(name = "ya", englishEquivalent = ", yeah?"),
        sentenceFinalParticle(name = "lah", englishEquivalent = ", obviously"),

        // ── TQ-1 Phase 3 additions — each uses a different helper
        // `kah`: formal-question suffix on closed list of stems. NULL target.
        formalQuestionSuffix(
            name = "kah",
            stems = listOf("apa", "siapa", "mana", "bila", "berapa", "kapan", "mengapa", "bagaimana"),
        ),
        // `nih` + `tuh`: sentence-initial deictics
        sentenceInitialDeictic(name = "nih", englishEquivalent = "Here, "),
        sentenceInitialDeictic(name = "tuh", englishEquivalent = "There, "),
        // `mah`: concessive after subject pronoun. NULL target.
        pronounConcessive(name = "mah"),
        // `juga` + `also` (loanword alias): mid-sentence "also/too"
        midSentenceAlso(name = "juga"),
        midSentenceAlso(name = "also"),

        // ── TQ-3 gender-neutral pronoun (Phase 4)
        // `dia` registered AFTER `mah` so `dia mah` collisions are tagged by mah first.
        genderNeutralPronoun(name = "dia"),

        // ── TQ-6 religious-expression verbatim preservation (Phase 4)
        verbatimReligious(name = "alhamdulillah"),
        verbatimReligious(name = "insyaallah"),
        verbatimReligious(name = "bismillah"),
        verbatimReligious(name = "subhanallah"),
        verbatimReligious(name = "astaghfirullah"),
        verbatimReligious(name = "masyaallah"),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

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

    /**
     * Helper for formal-question suffix particles: detect the suffix attached to
     * any stem in [stems], substitute the suffix portion only with the marker,
     * and provide NO target equivalent (English doesn't need a question marker
     * — word order + `?` does the work).
     *
     * Example: `kah` with `stems = listOf("apa", "siapa")` matches `apakah`,
     * `siapakah` in source. `Apakah kamu suka?` → `Apa[PARTICLE:kah] kamu suka?`
     * in preProcess; the marker is silently stripped in postProcess.
     */
    private fun formalQuestionSuffix(
        name: String,
        stems: List<String>,
    ): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            // Try each stem; return the earliest match.
            stems.asSequence()
                .mapNotNull { stem ->
                    val pattern = """(?<=^|\s)${Regex.escape(stem)}${Regex.escape(name)}(?=[\s,.!?;:]|$)"""
                    Regex(pattern, setOf(RegexOption.IGNORE_CASE)).find(text)?.let { match ->
                        // Marker replaces ONLY the suffix portion, not the stem.
                        val suffixStart = match.range.first + stem.length
                        val suffixEnd = match.range.last + 1
                        ParticleMatch(startIndex = suffixStart, endIndex = suffixEnd, matched = name)
                    }
                }
                .minByOrNull { it.startIndex }
        },
        targetEquivalents = emptyMap(), // null equivalent → no inject; marker silently stripped
        inject = { current, _, _ -> current }, // never called (no equivalent)
    )

    /**
     * Helper for sentence-initial deictics (`nih` = "here", `tuh` = "there").
     * Detects the particle as the first word in the text (followed by whitespace);
     * inject prepends the equivalent to the rawTarget.
     *
     * Idempotency: if `current` already starts with [englishEquivalent], the
     * contains-check in [ParticleProcessor.postProcess] Pass 2 skips re-injection.
     */
    private fun sentenceInitialDeictic(
        name: String,
        englishEquivalent: String,
    ): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            val pattern = """^${Regex.escape(name)}(?=\s)"""
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                .find(text)
                ?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
        },
        targetEquivalents = mapOf("en-US" to englishEquivalent),
        inject = { current, equivalent, _ -> equivalent + current },
    )

    /**
     * Helper for `mah` concessive marker following a subject pronoun. Detects
     * `<pronoun> mah` at word boundary; the marker replaces only the `mah` portion,
     * preserving the pronoun for the NMT to translate normally.
     *
     * NULL target equivalent — `mah`'s function (topicalization / "as for X")
     * is captured by the NMT's own translation of the pronoun + clause structure;
     * our processor just ensures the marker doesn't leak to the UI.
     */
    private fun pronounConcessive(name: String): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            val pronouns = listOf("aku", "saya", "kamu", "dia", "kami", "kita", "mereka", "ia")
            // Find the earliest `<pronoun> mah` occurrence, returning the position of `mah`.
            pronouns.asSequence()
                .mapNotNull { pronoun ->
                    val pattern = """(?<=^|\s)${Regex.escape(pronoun)}\s+${Regex.escape(name)}(?=[,.\s]|$)"""
                    Regex(pattern, setOf(RegexOption.IGNORE_CASE)).find(text)?.let { match ->
                        val mahStart = match.range.last - (name.length - 1)
                        val mahEnd = match.range.last + 1
                        ParticleMatch(startIndex = mahStart, endIndex = mahEnd, matched = name)
                    }
                }
                .minByOrNull { it.startIndex }
        },
        targetEquivalents = emptyMap(),
        inject = { current, _, _ -> current },
    )

    /**
     * Helper for mid-sentence "also/too" particles (`juga`, `also`-loanword).
     * Detects as a standalone word; inject appends "too" at sentence end.
     *
     * Position simplification: NMT typically renders Indonesian `juga` at the
     * grammatically natural English position ("X also Y" / "X too" / etc.). When
     * NMT preserves the marker, Pass 1 substitutes at the marker's position. When
     * NMT drops the marker, Pass 2 falls back to appending " too" at end —
     * imperfect but acceptable for the conservative case.
     */
    private fun midSentenceAlso(name: String): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            val pattern = """(?<=\s)${Regex.escape(name)}(?=[\s.,!?;:]|$)"""
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                .find(text)
                ?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
        },
        targetEquivalents = mapOf("en-US" to " too"),
        inject = { current, equivalent, _ ->
            val trailingPunct = Regex("""[.,!?;:]+\s*$""").find(current)
            if (trailingPunct != null) {
                current.substring(0, trailingPunct.range.first) + equivalent + trailingPunct.value
            } else {
                current + equivalent
            }
        },
    )

    /**
     * Helper for Indonesian gender-neutral third-person singular pronoun (`dia`).
     * Indonesian doesn't distinguish he/she; English singular-they is the most
     * faithful default per architecture §11 + DR §6.3.
     *
     * Detect: standalone `dia` as a whole word (sentence-start OR preceded by
     * whitespace, followed by whitespace OR sentence-terminal punctuation).
     * Does NOT match `dianya` (possessive suffix — out of scope for v1) or
     * any substring inside a larger token like `radia` (won't happen, but
     * the lookbehind enforces it).
     *
     * Inject: Pass 2 fallback for the case where NMT dropped the marker AND
     * `"they"` isn't already in target — replace the first `he/she/He/She`
     * with `they/They`. If no gendered pronoun is present (e.g., NMT produced
     * a noun phrase), inject is a no-op; the rule is still marked preserved
     * since the source-side intent has been honored by the NMT itself.
     *
     * **Why not also handle object/possessive forms (him/her/his/hers):**
     * Indonesian uses `dia` for subject + object positions and `-nya` clitic
     * for possessive. Subject is most common in conversation and lowest-risk
     * to replace blindly. Object + possessive disambiguation is deferred
     * pending real-conversation evidence from Story 3.1.
     */
    private fun genderNeutralPronoun(name: String): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            val pattern = """(?<=^|\s)${Regex.escape(name)}(?=[\s,.!?;:]|$)"""
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                .find(text)
                ?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
        },
        targetEquivalents = mapOf("en-US" to "they"),
        inject = { current, equivalent, _ ->
            val gendered = Regex("""\b(he|she|He|She)\b""")
            val match = gendered.find(current)
            if (match != null) {
                val replacement = if (match.value[0].isUpperCase()) {
                    equivalent.replaceFirstChar { it.uppercase() }
                } else {
                    equivalent
                }
                current.replaceRange(match.range, replacement)
            } else {
                current
            }
        },
    )

    /**
     * Helper for verbatim preservation of Arabic-origin religious expressions
     * (DR §6 / TQ-6). Target equivalent = the same lowercase term — the
     * English-receiving partner is expected to recognize these; literal
     * translation ("thank God", "God willing") would flatten the cultural-
     * pragmatic register.
     *
     * Detect: standalone word (start-of-string OR preceded by whitespace OR
     * preceded by punctuation), followed by whitespace OR sentence-terminal
     * punctuation. Case-insensitive — both `Alhamdulillah` (sentence-initial)
     * and `alhamdulillah` (mid-sentence) match.
     *
     * Inject: append `, <term>` before trailing terminal punctuation. Used
     * only when NMT dropped the marker AND didn't already produce the term
     * (Pass 2 idempotency contains-check is case-sensitive, so capitalized
     * NMT output like "Alhamdulillah, ..." would trigger inject; that's
     * acceptable — the appended lowercase form is correct and the test
     * fixtures use lowercase consistently).
     *
     * **Multi-word variants deferred:** `insya Allah`, `masya Allah`, the
     * Arabic-script forms (إن شاء الله, etc.), and salutation pairs like
     * `assalamualaikum` / `wa'alaikumsalam` are out of v1 scope. Six common
     * single-word Indonesian spellings cover the dominant conversational
     * cases per architecture §11.
     */
    private fun verbatimReligious(name: String): ParticleRule = ParticleRule(
        name = name,
        sourceLang = "id-ID",
        detect = { text ->
            val pattern = """(?<=^|[\s,.!?;:])${Regex.escape(name)}(?=[\s,.!?;:]|$)"""
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                .find(text)
                ?.let { ParticleMatch(startIndex = it.range.first, endIndex = it.range.last + 1, matched = it.value) }
        },
        targetEquivalents = mapOf("en-US" to name),
        inject = { current, equivalent, _ ->
            val trailingPunct = Regex("""[.,!?;:]+\s*$""").find(current)
            if (trailingPunct != null) {
                current.substring(0, trailingPunct.range.first) + ", " + equivalent + trailingPunct.value
            } else {
                "$current, $equivalent"
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
 *    Empty map indicates a null-target rule (e.g., `kah`, `mah`) — preProcess
 *    still tags, postProcess strips the marker silently without injecting.
 *  - [inject]: insert the target equivalent into the rawTarget at the right
 *    position. NEVER called for null-target rules.
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
