package com.xaeryx.translatorrep.translation.nmt

import com.xaeryx.translatorrep.translation.LanguageCode
import com.xaeryx.translatorrep.translation.TranslationResult

/**
 * Translates a settled source string to the target language (architecture Patterns §9 — Provider
 * Abstraction NFR). Symmetric with the iOS `TranslationProvider` protocol. The UI never calls a
 * `TranslationProvider` directly — everything flows through `CallSession` (Patterns §13), and the
 * production flow wraps the raw model in `RuleBasedTranslationProvider` (Story 3.10).
 *
 * Implementations: on-device NMT — NLLB-200 / MADLAD-400 / Gemma candidates (Story 3.8, ranked by
 * the Week-1 bake-off Story 3.9); Plan-B `VertexGeminiProvider`; [FakeTranslationProvider] (test).
 *
 * **Flores-200 codes stay inside here.** Inputs are BCP 47 [LanguageCode]s; any mapping to
 * Flores (`ind_Latn`, etc.) for the model call is a private impl detail and never escapes
 * (Patterns §3).
 */
interface TranslationProvider {

    /** Translate [sourceText] from [sourceLang] to [targetLang]. Suspends for the model call. */
    suspend fun translate(
        sourceText: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode,
    ): TranslationResult
}
