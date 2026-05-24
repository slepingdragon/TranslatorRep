package com.xaeryx.translatorrep.translation.particles

/**
 * Gen-Z 2026 Indonesian slang dictionary (DR §3 / TQ-8).
 *
 * **STUB — Story 3.2b.** ≥20 items per AC. Targets the slang vocabulary the
 * partner pair actually uses (informed by Story 3.1 pre-validation conversation
 * evidence). Implementation pattern: dictionary lookup with phrase-boundary
 * detection (slang often appears as one or two words inline, e.g., `gabut`,
 * `mager`, `gws`, `cuy`, `bestie`, `gass`, `santuy`, `mantap`, `auto`,
 * `wkwkwk`).
 *
 * Unlike TQ-1 particles, slang substitution generally happens by REPLACING
 * the slang token with a target-language equivalent at the same position
 * (e.g., `gabut → bored`), not by appending a discourse marker. The
 * [ParticleRule.inject] hook supports this — slang rules will register
 * their own positional inject functions.
 */
internal object GenZSlang {
    // TODO Story 3.2b: register ≥20 slang rules with positional inject.
}
