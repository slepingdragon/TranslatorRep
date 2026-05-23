package com.xaeryx.translatorrep.ids

import ulid.ULID

/**
 * Canonical ULID generator. All entity IDs in the project — Pair.id, Call.id,
 * Utterance.id, Caption.id, MessageId in Data Channel payloads — flow through
 * this object. Output is a 26-character Crockford base32 string, time-sortable
 * and collision-resistant at 2-user scale.
 *
 * See architecture §4 (ID Format — Locked Globally) and /shared/canonical-names.md §3.
 *
 * The runtime library is `com.aallam.ulid:ulid-kotlin` (Kotlin Multiplatform; the
 * published Kotlin package is `ulid`, not `com.aallam.ulid` — Maven coordinate
 * notwithstanding). Callers must never reach past this facade; if the library is
 * ever swapped, only this file changes. The static [encodeCanonical] helper is
 * library-independent (raw Crockford base32 encoding) and serves the cross-platform
 * parity test — it is the spec implementation; production code uses [next].
 */
object UlidGenerator {

    /**
     * Generate a fresh canonical 26-char Crockford base32 ULID using the current time.
     *
     * **Known limitations** (acceptable at the 2-user-per-pair scale per architecture §4,
     * documented here for future scaling work):
     *
     * - **Within-millisecond monotonicity is NOT guaranteed.** Two `next()` calls in
     *   the same ms share a 10-char timestamp prefix; lex order of the 16-char random
     *   tail is independent per call (random). A pure-spec strict-monotonic factory
     *   would be needed for ordered same-ms ID generation.
     * - **Wall-clock dependence (not monotonic clock).** `System.currentTimeMillis()`
     *   can move backward across NTP corrections or manual clock changes. Two `next()`
     *   calls separated by an NTP rewind can produce ULIDs whose lex order disagrees
     *   with the call order. Switching to `SystemClock.elapsedRealtime()` + a monotonic
     *   epoch base would resolve this for in-call ordering.
     */
    fun next(): String =
        // coerceAtLeast(0L) guards against a misconfigured device clock returning
        // a negative epoch (rare but possible — e.g., on a device whose battery
        // died and clock reset, or a buggy NTP step). The library's encodeCanonical
        // rejects negative timestamps with IllegalArgumentException; this coerce
        // ensures next() never throws on the wall-clock path. Same-instant collision
        // risk for the brief window post-coerce is acceptable at 2-user scale.
        ULID.randomULID(System.currentTimeMillis().coerceAtLeast(0L))

    /**
     * Spec-correct ULID encoding from explicit (48-bit timestamp, 80-bit random).
     * Library-independent — uses only Crockford base32 math. Both platforms call
     * the equivalent function from their respective UlidGenerator with the locked
     * test vector ([UlidParityTest]) and must produce byte-identical output.
     *
     * The Crockford base32 alphabet is `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (32 chars;
     * excludes I, L, O, U to avoid visual ambiguity).
     *
     * @param timestampMs Unix epoch milliseconds. Must be in `[0, 2^48 − 1]` — values
     *   outside this range throw IllegalArgumentException (no silent truncation).
     * @param random80BitBigEndian Exactly 10 bytes (80 bits) of random material in
     *   big-endian byte order. Anything other than 10 bytes throws IllegalArgumentException.
     * @return The canonical 26-character ULID string.
     */
    fun encodeCanonical(
        timestampMs: Long,
        random80BitBigEndian: ByteArray,
    ): String {
        require(random80BitBigEndian.size == 10) {
            "ULID random portion must be exactly 10 bytes (80 bits); got ${random80BitBigEndian.size}"
        }
        require(timestampMs >= 0) { "timestampMs must be non-negative; got $timestampMs" }
        require(timestampMs <= MAX_TIMESTAMP_MS) {
            "timestampMs must fit in 48 bits (≤ $MAX_TIMESTAMP_MS); got $timestampMs"
        }

        // Lay out 128 bits as a 16-byte buffer: 6-byte big-endian timestamp || 10-byte random.
        val buf = ByteArray(16)
        for (i in 0 until 6) {
            buf[5 - i] = ((timestampMs shr (i * 8)) and 0xFF).toByte()
        }
        random80BitBigEndian.copyInto(buf, destinationOffset = 6)

        // Encode 128 bits as 26 chars of Crockford base32. The encoding uses a 130-bit
        // space (the leading char carries only the top 2 bits of the 128-bit value),
        // so we prepend two zero bits and consume 5 bits at a time from the most
        // significant side.
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val out = CharArray(ULID_LEN)
        for (i in 0 until ULID_LEN) {
            // Bit position of the most significant bit of the i-th 5-bit group, counted
            // from the left of the 130-bit space (which is offset by 2 from the 128-bit
            // buffer because of the two prepended zeros).
            val bitOffsetFromLeft128 = (i * 5) - 2
            val idx = extract5BitsAt(buf, bitOffsetFromLeft128)
            out[i] = alphabet[idx]
        }
        return String(out)
    }

    private const val ULID_LEN = 26
    private const val MAX_TIMESTAMP_MS: Long = (1L shl 48) - 1L

    /**
     * Extract 5 bits from [buf] starting at [bitOffsetFromLeft] (counting from the
     * MSB of buf[0]). A negative offset is allowed and corresponds to the prepended
     * zero-padding bits of the ULID's 130-bit encoding space (only the very first
     * call to this function — for the leading char of the ULID — uses a negative
     * offset of -2, which yields 2 zero bits followed by the top 3 bits of buf[0]).
     */
    @Suppress("MagicNumber")
    private fun extract5BitsAt(buf: ByteArray, bitOffsetFromLeft: Int): Int {
        var value = 0
        for (bitIndex in 0 until 5) {
            val absoluteBit = bitOffsetFromLeft + bitIndex
            val bit = if (absoluteBit < 0) {
                0
            } else {
                val byteIndex = absoluteBit ushr 3
                val bitInByteFromMsb = absoluteBit and 0x07
                (buf[byteIndex].toInt() ushr (7 - bitInByteFromMsb)) and 0x01
            }
            value = (value shl 1) or bit
        }
        return value
    }
}
