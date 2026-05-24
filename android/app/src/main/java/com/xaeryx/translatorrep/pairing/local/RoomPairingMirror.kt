package com.xaeryx.translatorrep.pairing.local

import com.xaeryx.translatorrep.pairing.MirroredPair
import com.xaeryx.translatorrep.pairing.PairingMirror

/** Room-backed [PairingMirror] over the single-row [PairedPartnerDao] (Story 1.11). */
class RoomPairingMirror(private val dao: PairedPartnerDao) : PairingMirror {

    override suspend fun read(): MirroredPair? =
        dao.get()?.let { MirroredPair(it.pairId, it.partnerUid, it.partnerName) }

    override suspend fun save(pair: MirroredPair) {
        dao.upsert(
            PairedPartnerEntity(
                pairId = pair.pairId,
                partnerUid = pair.partnerUid,
                partnerName = pair.partnerName,
            ),
        )
    }

    override suspend fun clear() = dao.clear()
}
