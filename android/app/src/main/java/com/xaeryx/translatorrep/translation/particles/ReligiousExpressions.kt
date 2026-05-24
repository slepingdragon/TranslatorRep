package com.xaeryx.translatorrep.translation.particles

/**
 * Religious / Arabic-origin expression preservation (DR §6 / TQ-6).
 *
 * **STUB — Story 3.2b.** Indonesian conversation frequently uses Arabic-origin
 * religious expressions (`inshallah`, `alhamdulillah`, `astaghfirullah`,
 * `subhanallah`, `bismillah`, `wa'alaikumsalam`) that should be PRESERVED
 * VERBATIM in target text rather than literally translated ("God willing",
 * "thank God", etc.). The English-receiving partner is expected to know these
 * expressions; preserving them maintains the conversational register +
 * religious-cultural context.
 *
 * Implementation: dictionary of fixed phrases; rule.targetEquivalent returns
 * the same phrase unchanged for any target language. The `expected_processed`
 * fixture for these will mark them as `[PARTICLE:inshallah]` etc., and
 * postProcess substitutes the marker with the verbatim word.
 */
internal object ReligiousExpressions {
    // TODO Story 3.2b: dictionary + verbatim-preservation rules.
}
