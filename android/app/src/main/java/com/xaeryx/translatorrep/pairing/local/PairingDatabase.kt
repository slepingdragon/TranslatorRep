package com.xaeryx.translatorrep.pairing.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the pairing local mirror (Story 1.11). First Room use in the app; the
 * encrypted transcript-history DB (FR-21, SQLCipher) is a separate Epic-8 database.
 *
 * `exportSchema = false` — this is a tiny single-table mirror that is always rebuildable from
 * Firestore `/pairs`; we don't ship migration schemas for it (a destructive recreate on
 * version bump is acceptable since the data is a cache).
 */
@Database(entities = [PairedPartnerEntity::class], version = 1, exportSchema = false)
abstract class PairingDatabase : RoomDatabase() {
    abstract fun pairedPartnerDao(): PairedPartnerDao

    companion object {
        const val NAME = "translatorrep-pairing.db"
    }
}
