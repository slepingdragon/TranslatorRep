package com.xaeryx.translatorrep.translation.nmt

import com.xaeryx.translatorrep.translation.LanguageCode
import com.xaeryx.translatorrep.translation.RenderMode
import com.xaeryx.translatorrep.translation.TranslationResult
import com.xaeryx.translatorrep.translation.TranslationStatus

/**
 * Test double for [TranslationProvider] (Story 3.3) — returns a canned [result] and records the
 * last call, so CaptionStack / CallSession / decorator tests run without a real model.
 */
class FakeTranslationProvider(
    private val result: TranslationResult = TranslationResult(
        targetText = "",
        particlesPreserved = emptyList(),
        status = TranslationStatus.OK,
        renderMode = RenderMode.DEFAULT,
        confidence = 1.0,
    ),
) : TranslationProvider {

    var callCount: Int = 0
        private set
    var lastSourceText: String? = null
        private set
    var lastSourceLang: LanguageCode? = null
        private set
    var lastTargetLang: LanguageCode? = null
        private set

    override suspend fun translate(
        sourceText: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode,
    ): TranslationResult {
        callCount++
        lastSourceText = sourceText
        lastSourceLang = sourceLang
        lastTargetLang = targetLang
        return result
    }
}
