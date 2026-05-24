package com.xaeryx.translatorrep.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Unit tests for [LanguageCode] BCP 47 validation (Story 3.3, Patterns §3). */
class LanguageCodeTest {

    @Test
    fun `supported language constants carry their BCP 47 tags`() {
        assertEquals("id-ID", LanguageCode.INDONESIAN.bcp47)
        assertEquals("en-US", LanguageCode.ENGLISH.bcp47)
        assertEquals("su-ID", LanguageCode.SUNDANESE.bcp47)
    }

    @Test
    fun `a well-formed BCP 47 tag is accepted`() {
        assertEquals("fr-FR", LanguageCode("fr-FR").bcp47)
    }

    @Test
    fun `malformed tags are rejected`() {
        // Flores-200 codes must never be used as a LanguageCode (Patterns §3), plus other shapes.
        listOf("ind_Latn", "english", "id_ID", "ID-id", "id-IDX", "id", "", "id-ID ").forEach {
            assertThrows("expected '$it' to be rejected", IllegalArgumentException::class.java) {
                LanguageCode(it)
            }
        }
    }
}
