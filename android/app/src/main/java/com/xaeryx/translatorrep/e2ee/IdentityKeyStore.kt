package com.xaeryx.translatorrep.e2ee

import com.xaeryx.translatorrep.secure.SecureStorage

/**
 * The fake-able seam over secure storage of the X25519 identity **private** key (Story 1.12).
 * Mirrors the SDK-seam pattern from earlier pairing stories so [IdentityRepository]'s
 * generate-or-reuse logic is unit-testable without Android keystore APIs.
 */
interface IdentityKeyStore {
    /** The stored 32-byte private key, or `null` on a fresh install (first launch). */
    fun loadPrivateKey(): ByteArray?

    /** Persist the 32-byte private key (encrypted at rest). */
    fun savePrivateKey(privateKey: ByteArray)
}

/**
 * [IdentityKeyStore] backed by [SecureStorage] (EncryptedSharedPreferences). The private key
 * lives only here — never logged, never networked (ADR-A2). A reinstall wipes app storage, so
 * a new keypair is generated next launch (accepted v1 limitation — no cross-reinstall recovery).
 */
class SecureIdentityKeyStore(private val storage: SecureStorage) : IdentityKeyStore {

    override fun loadPrivateKey(): ByteArray? = storage.getBytes(KEY_IDENTITY_PRIVATE)

    override fun savePrivateKey(privateKey: ByteArray) = storage.putBytes(KEY_IDENTITY_PRIVATE, privateKey)

    private companion object {
        const val KEY_IDENTITY_PRIVATE = "x25519_identity_private_key"
    }
}
