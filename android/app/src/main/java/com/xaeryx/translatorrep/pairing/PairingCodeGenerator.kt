package com.xaeryx.translatorrep.pairing

import kotlin.random.Random

/**
 * Generates 6-digit decimal Pairing Codes (FR-2, Story 1.9). Pure + deterministic given a
 * seeded [Random] — the collision-retry behavior is unit-tested via
 * [PairingCodeAllocatorTest] without touching Firestore.
 *
 * Codes are zero-padded 6-character strings (e.g. `"042917"`), NOT integers — leading zeros
 * are significant and the value is only ever a document id / display string. The 10^6 space
 * is collision-resistant at 2-user scale (architecture §"Pairing — 6-digit code").
 */
class PairingCodeGenerator(private val random: Random = Random.Default) {

    /** A fresh random 6-digit code, e.g. `"042917"`. */
    fun generate(): String = buildString {
        repeat(CODE_LENGTH) { append(random.nextInt(RADIX)) }
    }

    /**
     * Return [code] with exactly one randomly-chosen digit replaced by a fresh random digit.
     * Used by [PairingCodeAllocator] on the rare collision — FR-2's "if a collision occurs,
     * one digit is regenerated and re-checked". The replacement digit may coincide with the
     * original (1-in-10); that is fine — the allocator re-checks the result either way.
     */
    fun withOneDigitChanged(code: String): String {
        require(code.length == CODE_LENGTH) { "expected a $CODE_LENGTH-digit code" }
        val position = random.nextInt(CODE_LENGTH)
        val replacement = random.nextInt(RADIX)
        return code.substring(0, position) + replacement + code.substring(position + 1)
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val RADIX = 10
    }
}
