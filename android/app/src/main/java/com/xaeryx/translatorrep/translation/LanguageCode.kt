package com.xaeryx.translatorrep.translation

/**
 * A BCP 47 language tag (architecture Patterns §3). Used everywhere a language is named in the
 * translation surface — ASR start, `TranslationProvider.translate` params, captions.
 *
 * **Flores-200 codes (`ind_Latn`, `eng_Latn`, `sun_Latn`) are NOT this type** and must appear
 * ONLY between `TranslationProvider.translate()` input and the model call (a private detail of
 * the on-device NMT provider, Story 3.8) — never in logs, error codes, captions, or this
 * wrapper. Keeping the public surface BCP 47 enforces that boundary.
 *
 * v1 supports [INDONESIAN], [ENGLISH], [SUNDANESE]; the [bcp47] string is validated to the
 * `xx-XX` shape so a malformed code fails fast at construction.
 */
@JvmInline
value class LanguageCode(val bcp47: String) {

    init {
        require(BCP47_PATTERN.matches(bcp47)) { "not a valid BCP 47 language tag: '$bcp47'" }
    }

    companion object {
        private val BCP47_PATTERN = Regex("^[a-z]{2}-[A-Z]{2}$")

        /** Bahasa Indonesia. */
        val INDONESIAN = LanguageCode("id-ID")

        /** English (US). */
        val ENGLISH = LanguageCode("en-US")

        /** Sundanese. */
        val SUNDANESE = LanguageCode("su-ID")
    }
}
