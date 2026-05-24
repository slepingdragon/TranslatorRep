package com.xaeryx.translatorrep.pairing

/** The locally-cached pair (Story 1.11) — what the Paired home needs without a network call. */
data class MirroredPair(val pairId: String, val partnerUid: String, val partnerName: String)

/**
 * The fake-able seam over local persistence of the pair (Story 1.11). Production impl is
 * Room-backed ([com.xaeryx.translatorrep.pairing.local.RoomPairingMirror]); tests use an
 * in-memory fake. Keeps [PairingStatusRepository] unit-testable without Room/Robolectric.
 */
interface PairingMirror {
    /** The mirrored pair, or `null` if not paired locally. */
    suspend fun read(): MirroredPair?

    /** Insert/replace the single mirrored pair. */
    suspend fun save(pair: MirroredPair)

    /** Clear the mirror (on unpair). */
    suspend fun clear()
}
