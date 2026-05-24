package com.xaeryx.translatorrep.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PairingStatusRepository.reconcile] (Story 1.11, FR-4) — folding a `/pairs`
 * snapshot into status + the local mirror — and [RemotePair.partnerOf]. Driven with
 * [runBlocking] over in-memory fakes (no Firestore/Room; JUnit4-only toolchain). The `start`
 * Flow wiring is the untested thin wrapper.
 */
class PairingStatusRepositoryTest {

    private fun repository(directory: FakeDirectory, mirror: FakeMirror) =
        PairingStatusRepository(directory, mirror, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `partnerOf returns the other member`() {
        val pair = RemotePair(PAIR_ID, memberA = "a", memberB = "b")
        assertEquals("b", pair.partnerOf("a"))
        assertEquals("a", pair.partnerOf("b"))
    }

    @Test
    fun `no remote pair - reports Unpaired and clears the mirror`() = runBlocking {
        val directory = FakeDirectory()
        val mirror = FakeMirror().apply { stored = MirroredPair(PAIR_ID, PARTNER_UID, "Ayu") }

        val status = repository(directory, mirror).reconcile(MY_UID, remote = null)

        assertEquals(PairingStatus.Unpaired, status)
        assertNull("mirror should be cleared when not paired", mirror.stored)
    }

    @Test
    fun `fresh pair - reports Paired, writes own pairId, mirrors with default name`() = runBlocking {
        val directory = FakeDirectory()
        val mirror = FakeMirror()

        val status = repository(directory, mirror)
            .reconcile(MY_UID, RemotePair(PAIR_ID, memberA = MY_UID, memberB = PARTNER_UID))

        assertEquals(PairingStatus.Paired(PAIR_ID, PARTNER_UID, "Partner"), status)
        assertEquals("own pairId written once on first discovery", listOf(MY_UID to PAIR_ID), directory.ensured)
        assertEquals(MirroredPair(PAIR_ID, PARTNER_UID, "Partner"), mirror.stored)
    }

    @Test
    fun `partner side - partnerUid is the other member when I am memberB`() = runBlocking {
        val directory = FakeDirectory()
        val mirror = FakeMirror()

        val status = repository(directory, mirror)
            .reconcile(MY_UID, RemotePair(PAIR_ID, memberA = PARTNER_UID, memberB = MY_UID))

        assertEquals(PairingStatus.Paired(PAIR_ID, PARTNER_UID, "Partner"), status)
    }

    @Test
    fun `performUnpair clears the mirror, reports Unpaired, deletes the pair and own pairId`() =
        runBlocking {
            val directory = FakeDirectory()
            val mirror = FakeMirror().apply { stored = MirroredPair(PAIR_ID, PARTNER_UID, "Ayu") }
            val repository = repository(directory, mirror)

            repository.performUnpair(MY_UID, PAIR_ID)

            assertEquals(PairingStatus.Unpaired, repository.status.value)
            assertNull("mirror cleared on unpair", mirror.stored)
            assertEquals(listOf(PAIR_ID), directory.deletedPairs)
            assertEquals(listOf(MY_UID), directory.clearedPairIdFor)
        }

    @Test
    fun `already-mirrored pair - does not rewrite own pairId and keeps the cached name`() =
        runBlocking {
            val directory = FakeDirectory()
            val mirror = FakeMirror().apply { stored = MirroredPair(PAIR_ID, PARTNER_UID, "Ayu") }

            val status = repository(directory, mirror)
                .reconcile(MY_UID, RemotePair(PAIR_ID, memberA = MY_UID, memberB = PARTNER_UID))

            assertEquals(PairingStatus.Paired(PAIR_ID, PARTNER_UID, "Ayu"), status)
            assertTrue("no redundant own-pairId write when already mirrored", directory.ensured.isEmpty())
        }

    private class FakeDirectory : PairDirectory {
        /** (uid, pairId) for each ensureOwnPairId call. */
        val ensured = mutableListOf<Pair<String, String>>()
        val deletedPairs = mutableListOf<String>()
        val clearedPairIdFor = mutableListOf<String>()
        override fun observePairFor(myUid: String): Flow<RemotePair?> = flowOf(null)
        override suspend fun findPairFor(myUid: String): RemotePair? = null
        override suspend fun ensureOwnPairId(myUid: String, pairId: String) {
            ensured += myUid to pairId
        }
        override suspend fun deletePair(pairId: String) { deletedPairs += pairId }
        override suspend fun clearOwnPairId(myUid: String) { clearedPairIdFor += myUid }
    }

    private class FakeMirror : PairingMirror {
        var stored: MirroredPair? = null
        override suspend fun read(): MirroredPair? = stored
        override suspend fun save(pair: MirroredPair) { stored = pair }
        override suspend fun clear() { stored = null }
    }

    private companion object {
        const val MY_UID = "me-uid"
        const val PARTNER_UID = "partner-uid"
        const val PAIR_ID = "01PAIRID0000000000000000AB"
    }
}
