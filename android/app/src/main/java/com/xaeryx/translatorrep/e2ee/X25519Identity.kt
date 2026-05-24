package com.xaeryx.translatorrep.e2ee

import com.google.crypto.tink.subtle.X25519

/**
 * X25519 key primitives for the long-term identity keypair (Story 1.12, ADR-A2) and — later —
 * the per-call ephemeral ECDH (Epic 5). Thin wrapper over Google Tink's vetted
 * `com.google.crypto.tink.subtle.X25519`, which yields **raw 32-byte** Curve25519 values
 * (matching iOS CryptoKit `Curve25519.KeyAgreement` for cross-platform byte parity, and the
 * Firestore `bytes`/`Blob` form published to `/users/{uid}/identityPub`).
 *
 * Pure JVM (Tink's `subtle.X25519` is a pure-Kotlin/Java RFC 7748 impl), so it is unit-tested
 * directly without Android. Callers never touch Tink — they use this object.
 *
 * Security: the private key is a raw `ByteArray`; it is NEVER logged, serialized to a String,
 * or sent over the network (ADR-A2). Only [publicKey] output leaves the device.
 */
object X25519Identity {

    /** A fresh 32-byte X25519 private key (clamped per RFC 7748 by Tink). */
    fun generatePrivateKey(): ByteArray = X25519.generatePrivateKey()

    /** The 32-byte X25519 public key for [privateKey]. Deterministic. */
    fun publicKey(privateKey: ByteArray): ByteArray = X25519.publicFromPrivate(privateKey)

    /** Raw-key length for both private and public X25519 values. */
    const val KEY_SIZE_BYTES: Int = 32
}
