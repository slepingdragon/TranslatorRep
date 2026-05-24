package com.xaeryx.translatorrep.e2ee

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [IdentityRepository.ensureIdentity] (Story 1.12, ADR-A2) — generate-or-reuse
 * → store → publish — over in-memory fakes + the real [X25519Identity] (pure JVM). Covers
 * AC-6(a) generation succeeds and the reuse-across-restart contract.
 */
class IdentityRepositoryTest {

    private fun repository(keyStore: FakeKeyStore, publisher: FakePublisher) =
        IdentityRepository(keyStore, publisher, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `first launch - generates, stores a 32-byte key, publishes the derived public key`() =
        runBlocking {
            val keyStore = FakeKeyStore()
            val publisher = FakePublisher()

            repository(keyStore, publisher).ensureIdentity(UID)

            val storedPrivate = requireNotNull(keyStore.stored) { "private key must be stored" }
            assertEquals(32, storedPrivate.size)
            assertEquals("generated + saved exactly once", 1, keyStore.saveCount)

            assertEquals(UID, publisher.lastUid)
            val publishedPublic = requireNotNull(publisher.lastPub) { "public key must be published" }
            assertEquals(32, publishedPublic.size)
            assertArrayEquals(X25519Identity.publicKey(storedPrivate), publishedPublic)
        }

    @Test
    fun `returning launch - reuses the stored key without regenerating`() = runBlocking {
        val existing = X25519Identity.generatePrivateKey()
        val keyStore = FakeKeyStore().apply { stored = existing }
        val publisher = FakePublisher()

        repository(keyStore, publisher).ensureIdentity(UID)

        assertEquals("must not regenerate when a key already exists", 0, keyStore.saveCount)
        assertArrayEquals(existing, keyStore.stored)
        assertArrayEquals(X25519Identity.publicKey(existing), publisher.lastPub)
    }

    private class FakeKeyStore : IdentityKeyStore {
        var stored: ByteArray? = null
        var saveCount = 0
            private set

        override fun loadPrivateKey(): ByteArray? = stored
        override fun savePrivateKey(privateKey: ByteArray) {
            stored = privateKey
            saveCount++
        }
    }

    private class FakePublisher : IdentityPublisher {
        var lastUid: String? = null
        var lastPub: ByteArray? = null

        override suspend fun publishIdentityPub(uid: String, publicKey: ByteArray) {
            lastUid = uid
            lastPub = publicKey
        }
    }

    private companion object {
        const val UID = "me-uid"
    }
}
