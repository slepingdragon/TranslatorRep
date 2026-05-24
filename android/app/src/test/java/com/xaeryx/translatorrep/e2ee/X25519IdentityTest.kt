package com.xaeryx.translatorrep.e2ee

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [X25519Identity] (Story 1.12, ADR-A2). Runs the real Tink `subtle.X25519`
 * (pure JVM), so it verifies AC-6(c) — 32-byte Curve25519 keys — directly.
 */
class X25519IdentityTest {

    @Test
    fun `generatePrivateKey returns 32 bytes`() {
        assertEquals(32, X25519Identity.KEY_SIZE_BYTES)
        assertEquals(X25519Identity.KEY_SIZE_BYTES, X25519Identity.generatePrivateKey().size)
    }

    @Test
    fun `publicKey is 32 bytes and deterministic for a given private key`() {
        val privateKey = X25519Identity.generatePrivateKey()

        val pub1 = X25519Identity.publicKey(privateKey)
        val pub2 = X25519Identity.publicKey(privateKey)

        assertEquals(32, pub1.size)
        assertArrayEquals("public key must be deterministic from the private key", pub1, pub2)
    }

    @Test
    fun `public key differs from the private key`() {
        val privateKey = X25519Identity.generatePrivateKey()
        assertFalse(privateKey.contentEquals(X25519Identity.publicKey(privateKey)))
    }

    @Test
    fun `distinct private keys yield distinct public keys`() {
        val a = X25519Identity.generatePrivateKey()
        val b = X25519Identity.generatePrivateKey()

        assertFalse("two generated private keys should differ", a.contentEquals(b))
        assertFalse(
            "distinct private keys should map to distinct public keys",
            X25519Identity.publicKey(a).contentEquals(X25519Identity.publicKey(b)),
        )
    }
}
