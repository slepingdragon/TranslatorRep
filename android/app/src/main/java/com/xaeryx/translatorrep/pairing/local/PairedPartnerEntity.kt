package com.xaeryx.translatorrep.pairing.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local mirror of the user's pairing relationship (Story 1.11, FR-4). A 2-user app has at
 * most ONE pair, so this is a single-row table pinned to [SINGLETON_ID]; reading the pair is
 * "the row at id 0, or none". Lets the Paired home render on launch with no network
 * (offline-degraded), and holds the partner UID recoverable without Firestore.
 *
 * [partnerName] defaults to "Partner" (FR-23) — actual display-name sharing arrives with
 * Story 8.5 via the `/pairs` doc (the deployed rules forbid reading the partner's `/users`
 * doc, so names cannot be read from `/users/{partnerUid}`).
 */
@Entity(tableName = "paired_partner")
data class PairedPartnerEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val pairId: String,
    val partnerUid: String,
    val partnerName: String,
) {
    companion object {
        /** The only row id — there is exactly one pair. */
        const val SINGLETON_ID = 0
    }
}
