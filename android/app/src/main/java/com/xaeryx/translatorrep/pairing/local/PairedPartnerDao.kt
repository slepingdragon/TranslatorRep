package com.xaeryx.translatorrep.pairing.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room DAO for the single-row [PairedPartnerEntity] mirror (Story 1.11). */
@Dao
interface PairedPartnerDao {

    /** The mirrored pair, or `null` if not paired locally. */
    @Query("SELECT * FROM paired_partner WHERE id = ${PairedPartnerEntity.SINGLETON_ID} LIMIT 1")
    suspend fun get(): PairedPartnerEntity?

    /** Insert or replace the single mirror row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PairedPartnerEntity)

    /** Clear the mirror (on unpair, Story 1.13). */
    @Query("DELETE FROM paired_partner")
    suspend fun clear()
}
