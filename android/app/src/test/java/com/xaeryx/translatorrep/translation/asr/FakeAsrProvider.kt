package com.xaeryx.translatorrep.translation.asr

import com.xaeryx.translatorrep.translation.LanguageCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test double for [AsrProvider] (Story 3.3) — lets CaptionStack / CallSession tests run without
 * burning real ASR cost. Also models the §9 cancellation contract: [start] keeps its stream open
 * (like a live engine) until [stop], and [resourcesAllocated] tracks the "engine running" state
 * so a test can assert resources are released after [stop].
 *
 * Emits the scripted [partials] → optional [errorCode] → optional [finalText], then holds the
 * stream open until stopped.
 */
class FakeAsrProvider(
    override val supportsStreamingPartials: Boolean = true,
    private val partials: List<String> = emptyList(),
    private val finalText: String? = null,
    private val errorCode: String? = null,
) : AsrProvider {

    /** Mirrors a real engine's allocated resources: true while [start]'s flow is live. */
    var resourcesAllocated: Boolean = false
        private set

    private val stopSignal = CompletableDeferred<Unit>()

    override fun start(language: LanguageCode): Flow<AsrEvent> = flow {
        resourcesAllocated = true
        try {
            partials.forEach { emit(AsrEvent.Partial(it)) }
            errorCode?.let { emit(AsrEvent.Error(it)) }
            finalText?.let { emit(AsrEvent.Final(it)) }
            stopSignal.await() // keep the stream open until stop() / cancellation
        } finally {
            resourcesAllocated = false
        }
    }

    override fun stop() {
        if (!stopSignal.isCompleted) stopSignal.complete(Unit)
    }
}
