package com.xaeryx.translatorrep.pairing

/**
 * App-wide pairing status (Story 1.11, FR-4), driven by [PairingStatusRepository] from the
 * local Room mirror (offline-first) reconciled with a live `/pairs`-membership listener.
 * MainActivity routes on this: [Paired] → Paired home, [Unpaired] → Paired-Empty home,
 * [Unknown] → a brief loading gate on cold launch before either source resolves.
 */
sealed interface PairingStatus {

    /** Not yet resolved (cold launch, before the mirror read / first listener emission). */
    data object Unknown : PairingStatus

    /** Confirmed not paired — show the Paired-Empty home (enter a partner code). */
    data object Unpaired : PairingStatus

    /**
     * Paired. [partnerName] defaults to "Partner" (FR-23) until Story 8.5 adds display-name
     * sharing via the `/pairs` doc (the deployed rules forbid reading the partner's `/users`
     * doc, so the name can't come from `/users/{partnerUid}`).
     */
    data class Paired(
        val pairId: String,
        val partnerUid: String,
        val partnerName: String,
    ) : PairingStatus
}
