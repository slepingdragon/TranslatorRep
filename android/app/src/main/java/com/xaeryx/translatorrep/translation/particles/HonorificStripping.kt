package com.xaeryx.translatorrep.translation.particles

/**
 * Partner honorific detection + strip rules (DR §6 / TQ-5).
 *
 * **STUB — Story 3.2b.** Indonesian honorifics like `mbak`, `mas`, `kak`,
 * `pak`, `bu` carry social register information that often shouldn't translate
 * literally ("ma'am", "sir", etc.) for the intimate-partner conversational
 * register this app targets. Story 3.2b implements detection + rules for
 * stripping (when register is friendly/intimate) or preserving (when register
 * is formal). PRD FR-21 + DR §6 are the source of truth for which to do when.
 */
internal object HonorificStripping {
    // TODO Story 3.2b: detect + strip/preserve honorifics per DR §6 rules.
}
