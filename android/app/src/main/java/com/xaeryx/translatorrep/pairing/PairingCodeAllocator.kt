package com.xaeryx.translatorrep.pairing

/**
 * Allocates pairing codes against a [CodeStore] (Story 1.9, FR-2). This is the unit-testable
 * heart of the feature: generate → collision-check → (on collision) change one digit and
 * re-check → persist. Pure suspend functions over the fake-able [CodeStore] + a seedable
 * [PairingCodeGenerator], so [PairingCodeAllocatorTest] covers it without Firestore.
 *
 * [PairingViewModel] is a thin wrapper that calls these on `viewModelScope`.
 */
class PairingCodeAllocator(
    private val generator: PairingCodeGenerator,
    private val codeStore: CodeStore,
) {

    /**
     * The caller's pairing code: reuse their existing one if present (so a returning user
     * keeps the code their partner may already hold), otherwise [allocate] a fresh one.
     */
    suspend fun obtain(ownerId: String): String =
        codeStore.findOwnedCode(ownerId) ?: allocate(ownerId)

    /**
     * Replace the caller's [current] code with a freshly-allocated one: delete the old doc
     * (invalidating it, FR-2) then allocate + persist a new code. Used by the long-press
     * "Regenerate code" affordance.
     */
    suspend fun regenerate(ownerId: String, current: String): String {
        codeStore.delete(current)
        return allocate(ownerId)
    }

    /**
     * Generate a collision-free code and persist it at `/codes/{code}` owned by [ownerId].
     * On the (vanishingly rare at 2-user scale) collision, one digit is regenerated and
     * re-checked. [MAX_ATTEMPTS] guards against an infinite loop; exhausting it throws, which
     * the ViewModel surfaces as an error state rather than overwriting someone else's code.
     */
    private suspend fun allocate(ownerId: String): String {
        var code = generator.generate()
        var attempts = 0
        while (codeStore.exists(code)) {
            check(attempts < MAX_ATTEMPTS) { "could not allocate a free pairing code" }
            code = generator.withOneDigitChanged(code)
            attempts++
        }
        codeStore.create(code, ownerId)
        return code
    }

    private companion object {
        const val MAX_ATTEMPTS = 10
    }
}
