package com.xaeryx.translatorrep.translation.particles

/**
 * Indirect refusal preservation (DR §6 / TQ-7).
 *
 * **STUB — Story 3.2b.** Indonesian pragmatics often expresses "no" indirectly
 * (`belum`, `nanti aja`, `mungkin nanti`, etc.) — translating these as
 * literal "not yet" or "maybe later" loses the polite-refusal force. Story
 * 3.2b implements detection + injection of pragmatic markers so the English
 * side reads as the actual social meaning, not the literal lexical content.
 *
 * DR §6 has the full taxonomy. Worth real-conversation evidence (Story 3.1)
 * before pinning down the rule set — some refusal patterns may be specific
 * to the partner-pair register and not generalize.
 */
internal object IndirectRefusals {
    // TODO Story 3.2b: detect + annotate indirect refusals per DR §6.
}
