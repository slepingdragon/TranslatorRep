package com.xaeryx.translatorrep.ids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Validates that [UlidGenerator.next] produces canonical ULIDs (26-char Crockford
 * base32) and that 1000 consecutive calls are all unique.
 *
 * The cross-platform parity test (verification against the locked test vector
 * from /shared/canonical-names.md) lives in [UlidParityTest].
 */
class UlidGeneratorTest {

    /** Crockford base32 alphabet — 32 chars, excludes I, L, O, U. */
    private val crockfordRegex = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")

    @Test
    fun `next produces 26-char canonical Crockford base32`() {
        repeat(N_SAMPLES) {
            val id = UlidGenerator.next()
            assertEquals("Expected 26-char ULID, got '$id' (${id.length} chars)", ULID_LEN, id.length)
            assertTrue(
                "ULID '$id' contains characters outside the Crockford base32 alphabet",
                crockfordRegex.matches(id),
            )
        }
    }

    @Test
    fun `1000 consecutive ULIDs are unique`() {
        val seen = HashSet<String>(N_SAMPLES * 2)
        for (iteration in 0 until N_SAMPLES) {
            val id = UlidGenerator.next()
            assertTrue(
                "Duplicate ULID '$id' observed at iteration $iteration — collision-resistance violated",
                seen.add(id),
            )
        }
        assertEquals(N_SAMPLES, seen.size)
    }

    @Test
    fun `ULIDs generated in different milliseconds are time-sortable`() {
        // Across millisecond boundaries the timestamp portion (first 10 chars) is
        // monotonic. We assert ONLY the timestamp prefix, not the full string —
        // the 16-char random tail is independent per call and may sort either way
        // when same-ms (which would flake the test on Windows where the timer-tick
        // is coarser than the sleep). 50 ms is well above the Windows ~15.6 ms
        // default tick granularity.
        val first = UlidGenerator.next()
        Thread.sleep(SLEEP_MS_FOR_MONOTONICITY)
        val second = UlidGenerator.next()
        assertNotEquals(first, second)
        val firstTimestampPrefix = first.substring(0, TIMESTAMP_PREFIX_LEN)
        val secondTimestampPrefix = second.substring(0, TIMESTAMP_PREFIX_LEN)
        assertTrue(
            "Expected time-sortable ULIDs: timestamp prefix '$firstTimestampPrefix' " +
                "(of '$first') should sort <= '$secondTimestampPrefix' (of '$second')",
            firstTimestampPrefix < secondTimestampPrefix,
        )
    }

    @Test
    fun `encodeCanonical rejects wrong-length random material`() {
        val ts = 1779458031242L
        try {
            UlidGenerator.encodeCanonical(ts, ByteArray(9))
            fail("Expected IllegalArgumentException for 9-byte random; got success")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Unexpected error message: ${e.message}",
                e.message!!.contains("10 bytes"),
            )
        }
        try {
            UlidGenerator.encodeCanonical(ts, ByteArray(11))
            fail("Expected IllegalArgumentException for 11-byte random; got success")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Unexpected error message: ${e.message}",
                e.message!!.contains("10 bytes"),
            )
        }
    }

    private companion object {
        const val N_SAMPLES = 1000
        const val ULID_LEN = 26
        const val TIMESTAMP_PREFIX_LEN = 10
        const val SLEEP_MS_FOR_MONOTONICITY = 50L
    }
}
