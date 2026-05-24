package com.xaeryx.translatorrep.translation.asr

import com.xaeryx.translatorrep.translation.LanguageCode
import kotlinx.coroutines.flow.Flow

/**
 * On-device speech recognition (architecture Patterns §9 — Provider Abstraction NFR). Symmetric
 * with the iOS `AsrProvider` protocol (identical method names + shapes) so providers swap behind
 * the abstraction without rewriting call sites. The UI never touches an `AsrProvider` directly —
 * everything flows through `CallSession` (Patterns §13).
 *
 * Implementations: Android `OnDeviceAsrProvider` (Story 3.4, `SpeechRecognizer`); iOS
 * whisper.cpp (Story 3.5). [FakeAsrProvider] (test) models the contract for caption tests.
 */
interface AsrProvider {

    /**
     * Whether this engine emits [AsrEvent.Partial] interim hypotheses (Android `SpeechRecognizer`
     * does; whisper.cpp does not — the iOS side shows a speaking indicator instead, the "honest
     * asymmetry" of Story 3.13).
     */
    val supportsStreamingPartials: Boolean

    /**
     * Begin recognizing [language] audio and emit [AsrEvent]s until [stop] (or flow cancellation).
     * Collecting the returned [Flow] starts the engine; the flow completes when recognition ends.
     */
    fun start(language: LanguageCode): Flow<AsrEvent>

    /**
     * Stop recognition and release ALL engine resources. Per the §9 cancellation contract this
     * MUST terminate the [start] stream and free resources within 500 ms. Idempotent.
     */
    fun stop()
}
