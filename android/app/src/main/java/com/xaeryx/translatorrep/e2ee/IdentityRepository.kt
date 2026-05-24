package com.xaeryx.translatorrep.e2ee

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ensures the device has a long-term X25519 identity keypair and that its public half is
 * published (Story 1.12, ADR-A2). App-wide singleton held by
 * [com.xaeryx.translatorrep.TranslatorRepApplication]; kicked off once the user is signed in.
 *
 * [ensureIdentity] is the unit-tested core (pure suspend over the fake-able [IdentityKeyStore]
 * + [IdentityPublisher] seams, with the real pure-JVM [X25519Identity]); [start] is the thin
 * fire-and-forget wrapper. The private key is generated once and persisted in secure storage;
 * it is never logged or networked — only the derived public key is published.
 */
class IdentityRepository(
    private val keyStore: IdentityKeyStore,
    private val publisher: IdentityPublisher,
    private val scope: CoroutineScope,
) {

    private var started = false

    /**
     * Ensure + publish the identity for [uid], once per process. Best-effort: a publish failure
     * (e.g. offline) is swallowed and retried on the next launch — the private key is already
     * persisted, so no key material is lost.
     */
    fun start(uid: String) {
        if (started) return
        started = true
        scope.launch { runCatching { ensureIdentity(uid) } }
    }

    /**
     * Load the existing private key or generate + persist a new one (first launch), then
     * publish the derived public key. Idempotent: a returning launch reuses the stored key
     * (no regeneration) and re-publishes the same public key (harmless merge).
     */
    suspend fun ensureIdentity(uid: String) {
        val privateKey = keyStore.loadPrivateKey()
            ?: X25519Identity.generatePrivateKey().also { keyStore.savePrivateKey(it) }
        publisher.publishIdentityPub(uid, X25519Identity.publicKey(privateKey))
    }
}
