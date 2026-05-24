package com.xaeryx.translatorrep.translation.nmt

import com.xaeryx.translatorrep.translation.LanguageCode
import com.xaeryx.translatorrep.translation.RenderMode
import com.xaeryx.translatorrep.translation.TranslationResult
import com.xaeryx.translatorrep.translation.TranslationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sanity test for [FakeTranslationProvider] (Story 3.3) — confirms the test double records its
 * call and returns the canned [TranslationResult], so downstream tests can trust it.
 */
class FakeTranslationProviderTest {

    @Test
    fun `translate records the call and returns the canned result`() = runBlocking {
        val canned = TranslationResult(
            targetText = "you know",
            particlesPreserved = listOf("loh"),
            status = TranslationStatus.OK,
            renderMode = RenderMode.DEFAULT,
            confidence = 0.9,
        )
        val provider = FakeTranslationProvider(canned)

        val result = provider.translate("loh", LanguageCode.INDONESIAN, LanguageCode.ENGLISH)

        assertEquals(canned, result)
        assertEquals(1, provider.callCount)
        assertEquals("loh", provider.lastSourceText)
        assertEquals(LanguageCode.INDONESIAN, provider.lastSourceLang)
        assertEquals(LanguageCode.ENGLISH, provider.lastTargetLang)
    }
}
