package com.xaeryx.translatorrep.pairing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PairingCoordinator] (Story 1.10, FR-3) — the lookup → validate → create-pair
 * core, driven with [runBlocking] over in-memory fakes (no Firestore; JUnit4-only toolchain).
 * The pair-id factory + clock are injected for determinism.
 */
class PairingCoordinatorTest {

    private val now = 1_000_000L

    private fun coordinator(codeStore: FakeCodeStore, pairStore: FakePairStore) =
        PairingCoordinator(
            codeStore = codeStore,
            pairStore = pairStore,
            pairIdFactory = { FIXED_PAIR_ID },
            now = { now },
        )

    @Test
    fun `unknown code returns NotFound and writes nothing`() = runBlocking {
        val codeStore = FakeCodeStore()
        val pairStore = FakePairStore()

        val result = coordinator(codeStore, pairStore).pair(MY_UID, "000000")

        assertEquals(PairResult.NotFound, result)
        assertTrue(pairStore.createdPairs.isEmpty())
        assertTrue(pairStore.userPairIds.isEmpty())
    }

    @Test
    fun `own code returns OwnCode and writes nothing`() = runBlocking {
        val codeStore = FakeCodeStore().apply {
            records["482917"] = CodeRecord(ownerId = MY_UID, expiresAtMillis = now + 1000)
        }
        val pairStore = FakePairStore()

        val result = coordinator(codeStore, pairStore).pair(MY_UID, "482917")

        assertEquals(PairResult.OwnCode, result)
        assertTrue(pairStore.createdPairs.isEmpty())
    }

    @Test
    fun `expired code returns Expired and writes nothing`() = runBlocking {
        val codeStore = FakeCodeStore().apply {
            records["482917"] = CodeRecord(ownerId = PARTNER_UID, expiresAtMillis = now - 1)
        }
        val pairStore = FakePairStore()

        val result = coordinator(codeStore, pairStore).pair(MY_UID, "482917")

        assertEquals(PairResult.Expired, result)
        assertTrue(pairStore.createdPairs.isEmpty())
    }

    @Test
    fun `valid code creates the pair (caller is memberA) and writes the caller's own pairId`() =
        runBlocking {
            val codeStore = FakeCodeStore().apply {
                records["482917"] = CodeRecord(ownerId = PARTNER_UID, expiresAtMillis = now + 1000)
            }
            val pairStore = FakePairStore()

            val result = coordinator(codeStore, pairStore).pair(MY_UID, "482917")

            assertEquals(PairResult.Success(FIXED_PAIR_ID), result)
            assertEquals(
                "pair created with caller as memberA, owner as memberB",
                listOf(Triple(FIXED_PAIR_ID, MY_UID, PARTNER_UID)),
                pairStore.createdPairs,
            )
            // Only the caller's own /users doc is written (rules forbid writing the partner's).
            assertEquals(mapOf(MY_UID to FIXED_PAIR_ID), pairStore.userPairIds)
        }

    @Test
    fun `a null expiresAt is treated as non-expiring`() = runBlocking {
        val codeStore = FakeCodeStore().apply {
            records["482917"] = CodeRecord(ownerId = PARTNER_UID, expiresAtMillis = null)
        }
        val pairStore = FakePairStore()

        val result = coordinator(codeStore, pairStore).pair(MY_UID, "482917")

        assertEquals(PairResult.Success(FIXED_PAIR_ID), result)
    }

    private class FakeCodeStore : CodeStore {
        val records = linkedMapOf<String, CodeRecord>()
        override suspend fun findOwnedCode(ownerId: String): String? =
            records.entries.firstOrNull { it.value.ownerId == ownerId }?.key
        override suspend fun exists(code: String): Boolean = records.containsKey(code)
        override suspend fun lookup(code: String): CodeRecord? = records[code]
        override suspend fun create(code: String, ownerId: String) {
            records[code] = CodeRecord(ownerId, expiresAtMillis = null)
        }
        override suspend fun delete(code: String) { records.remove(code) }
    }

    private class FakePairStore : PairStore {
        /** (pairId, memberA, memberB) for each createPair call. */
        val createdPairs = mutableListOf<Triple<String, String, String>>()
        val userPairIds = linkedMapOf<String, String>()

        override suspend fun createPair(pairId: String, memberA: String, memberB: String) {
            createdPairs += Triple(pairId, memberA, memberB)
        }

        override suspend fun setUserPairId(uid: String, pairId: String) {
            userPairIds[uid] = pairId
        }
    }

    private companion object {
        const val MY_UID = "me-uid"
        const val PARTNER_UID = "partner-uid"
        const val FIXED_PAIR_ID = "01PAIRID0000000000000000AB"
    }
}
