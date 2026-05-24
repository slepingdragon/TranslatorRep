package com.xaeryx.translatorrep.translation.asr

import com.xaeryx.translatorrep.translation.LanguageCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the §9 cancellation contract for the [AsrProvider] surface (Story 3.3) against
 * [FakeAsrProvider]: `stop()` MUST terminate the [AsrProvider.start] stream and release all
 * resources within 500 ms. Real engines (Story 3.4 Android, 3.5 iOS) get their own contract
 * tests; this proves the contract is expressible + the reference fake models it.
 */
class AsrProviderCancellationTest {

    @Test
    fun `stop terminates the stream and releases resources within 500 ms`() = runBlocking {
        val provider = FakeAsrProvider(partials = listOf("hel", "hello"), finalText = "hello there")
        val collected = mutableListOf<AsrEvent>()

        // UNDISPATCHED: run the collector inline until it suspends — it emits the scripted events
        // and then parks on the open stream, with resources allocated.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.start(LanguageCode.ENGLISH).collect { collected += it }
        }

        assertTrue("engine should be allocated while the stream is live", provider.resourcesAllocated)
        assertEquals(
            listOf(AsrEvent.Partial("hel"), AsrEvent.Partial("hello"), AsrEvent.Final("hello there")),
            collected,
        )

        provider.stop()
        withTimeout(STOP_BUDGET_MS) { job.join() } // stream must terminate within the budget

        assertFalse("resources must be released after stop()", provider.resourcesAllocated)
    }

    @Test
    fun `stop is idempotent`() = runBlocking {
        val provider = FakeAsrProvider()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            provider.start(LanguageCode.ENGLISH).collect { }
        }

        provider.stop()
        provider.stop() // second call must not throw
        withTimeout(STOP_BUDGET_MS) { job.join() }

        assertFalse(provider.resourcesAllocated)
    }

    private companion object {
        const val STOP_BUDGET_MS = 500L
    }
}
