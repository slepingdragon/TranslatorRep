package com.xaeryx.translatorrep.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [PairingCodeAllocator] (Story 1.9, FR-2) — the generate / collision-check /
 * reuse / regenerate core, driven with [runBlocking] over an in-memory [FakeCodeStore] and a
 * real seeded [PairingCodeGenerator] (no Firestore, matching the JUnit4-only toolchain).
 */
class PairingCodeAllocatorTest {

    private val sixDigits = Regex("^[0-9]{6}$")

    private fun allocator(store: FakeCodeStore) =
        PairingCodeAllocator(PairingCodeGenerator(Random(SEED)), store)

    @Test
    fun `obtain allocates and persists a fresh code when none exists`() = runBlocking {
        val store = FakeCodeStore()

        val code = allocator(store).obtain(OWNER)

        assertTrue("'$code' is not a 6-digit code", sixDigits.matches(code))
        assertEquals("the code should be persisted to its owner", OWNER, store.stored[code])
        assertEquals(listOf(code), store.createdCodes)
    }

    @Test
    fun `obtain reuses the caller's existing code without generating a new one`() = runBlocking {
        val store = FakeCodeStore().apply { stored["111111"] = OWNER }

        val code = allocator(store).obtain(OWNER)

        assertEquals("111111", code)
        assertTrue("no new code should be created on reuse", store.createdCodes.isEmpty())
        assertTrue("no collision check needed on reuse", store.existsArgs.isEmpty())
    }

    @Test
    fun `collision at generation triggers a one-digit retry, then persists a free code`() =
        runBlocking {
            // Report the first candidate as taken, forcing exactly one withOneDigitChanged retry.
            val store = FakeCodeStore().apply { collideFirstN = 1 }

            val code = allocator(store).obtain(OWNER)

            assertEquals("expected one collision then one free check", 2, store.existsArgs.size)
            assertEquals("the free (second) candidate is the one persisted", store.existsArgs[1], code)
            assertEquals(listOf(code), store.createdCodes)
        }

    @Test
    fun `regenerate deletes the current code and persists a new one`() = runBlocking {
        val store = FakeCodeStore().apply { stored["111111"] = OWNER }

        val newCode = allocator(store).regenerate(OWNER, current = "111111")

        assertEquals(listOf("111111"), store.deletedCodes)
        assertFalse("old code must be invalidated", store.stored.containsKey("111111"))
        assertNotEquals("111111", newCode)
        assertEquals(OWNER, store.stored[newCode])
    }

    @Test
    fun `allocation that never finds a free code throws rather than overwriting`() {
        // Always collide — exhausts the retry budget.
        val store = FakeCodeStore().apply { collideFirstN = Int.MAX_VALUE }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { allocator(store).obtain(OWNER) }
        }
        assertTrue("must not persist a colliding code", store.createdCodes.isEmpty())
    }

    /** In-memory [CodeStore] fake — no MockK. */
    private class FakeCodeStore : CodeStore {
        val stored = linkedMapOf<String, String>()
        val existsArgs = mutableListOf<String>()
        val createdCodes = mutableListOf<String>()
        val deletedCodes = mutableListOf<String>()

        /** Report the first N `exists` calls as taken, to simulate collisions. */
        var collideFirstN = 0

        override suspend fun findOwnedCode(ownerId: String): String? =
            stored.entries.firstOrNull { it.value == ownerId }?.key

        override suspend fun lookup(code: String): CodeRecord? =
            stored[code]?.let { CodeRecord(ownerId = it, expiresAtMillis = null) }

        override suspend fun exists(code: String): Boolean {
            existsArgs += code
            return if (existsArgs.size <= collideFirstN) true else stored.containsKey(code)
        }

        override suspend fun create(code: String, ownerId: String) {
            stored[code] = ownerId
            createdCodes += code
        }

        override suspend fun delete(code: String) {
            stored.remove(code)
            deletedCodes += code
        }
    }

    private companion object {
        const val OWNER = "owner-uid-1"
        const val SEED = 99L
    }
}
