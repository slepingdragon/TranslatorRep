package com.xaeryx.translatorrep.ids

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cross-platform ULID parity verification (Story 1.5, /shared/canonical-names.md §3).
 *
 * The locked test vector:
 * - timestamp_ms = 1779458031242 (= 2026-05-22T13:53:51.242Z)
 * - random_80bit_hex = 0102030405060708090A
 * - expected_ulid = "01KS7ZDFMA041061050R3GG28A"
 *
 * The iOS [UlidGenerator.encodeCanonical] equivalent (deferred to Story 1.2) must
 * produce the same string from the same input. This test exercises the
 * library-independent Crockford base32 encoder; the production [UlidGenerator.next]
 * delegates to com.aallam.ulid:ulid-kotlin.
 */
class UlidParityTest {

    @Test
    fun `encodeCanonical reproduces the locked cross-platform test vector`() {
        val timestampMs = 1779458031242L
        val random10Bytes = hexToBytes("0102030405060708090A")
        val expectedUlid = "01KS7ZDFMA041061050R3GG28A"

        val actualUlid = UlidGenerator.encodeCanonical(timestampMs, random10Bytes)

        assertEquals(
            """
            Cross-platform ULID parity test failed.

              Input:
                timestamp_ms      = $timestampMs (2026-05-22T13:53:51.242Z)
                random_80bit_hex  = 0102030405060708090A
              Expected ULID:      $expectedUlid
              Actual ULID:        $actualUlid

            If iOS produces the same actual value, update /shared/canonical-names.md §3
            with the new expected value. If iOS and Android disagree, one library is
            off-spec — switch it.
            """.trimIndent(),
            expectedUlid,
            actualUlid,
        )
    }

    @Test
    fun `encodeCanonical zero vector produces all-zero ULID`() {
        // 48 + 80 = 128 zero bits → all-zero ULID.
        val zeroUlid = UlidGenerator.encodeCanonical(0L, ByteArray(10))
        assertEquals("00000000000000000000000000", zeroUlid)
    }

    @Test
    fun `encodeCanonical max timestamp + max random produces all-Z payload`() {
        // 48-bit max timestamp = (1L shl 48) - 1 = 0xFFFFFFFFFFFF
        // 80-bit max random = ten 0xFF bytes
        // Together that's 128 one-bits; encoded in 130-bit space (top 2 bits forced
        // to 0), the leading char is binary 00111 = 7, rest are 11111 = Z.
        val maxTs = (1L shl 48) - 1L
        val maxRandom = ByteArray(10) { 0xFF.toByte() }
        val ulid = UlidGenerator.encodeCanonical(maxTs, maxRandom)
        assertEquals("7ZZZZZZZZZZZZZZZZZZZZZZZZZ", ulid)
    }

    /** Decode a hex string to raw bytes. Length must be even. */
    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length: '$hex'" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character in '$hex'" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
