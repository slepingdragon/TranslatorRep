package com.xaeryx.translatorrep.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [PairingCodeGenerator] (Story 1.9, FR-2) — pure RNG, no Firestore.
 */
class PairingCodeGeneratorTest {

    private val sixDigits = Regex("^[0-9]{6}$")

    @Test
    fun `generate produces a 6-digit decimal string (leading zeros allowed)`() {
        val generator = PairingCodeGenerator(Random(SEED))
        repeat(SAMPLES) {
            val code = generator.generate()
            assertTrue("'$code' is not a 6-digit decimal code", sixDigits.matches(code))
        }
    }

    @Test
    fun `generate is deterministic for a given seed`() {
        val a = PairingCodeGenerator(Random(SEED)).generate()
        val b = PairingCodeGenerator(Random(SEED)).generate()
        assertEquals(a, b)
    }

    @Test
    fun `withOneDigitChanged keeps length 6 and changes at most one position`() {
        val generator = PairingCodeGenerator(Random(SEED))
        repeat(SAMPLES) {
            val original = generator.generate()
            val changed = generator.withOneDigitChanged(original)

            assertTrue("'$changed' is not a 6-digit decimal code", sixDigits.matches(changed))
            val differingPositions = original.indices.count { original[it] != changed[it] }
            // Exactly one position is targeted; the fresh random digit may equal the original
            // (1-in-10), so 0 or 1 actual differences — never more.
            assertTrue(
                "withOneDigitChanged altered $differingPositions positions (expected ≤ 1)",
                differingPositions <= 1,
            )
        }
    }

    private companion object {
        const val SEED = 1234L
        const val SAMPLES = 200
    }
}
