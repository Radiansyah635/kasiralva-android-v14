package com.kasiralva.basic.data

import androidx.room.*

@Dao
interface StoreSettingsDao {
    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): StoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: StoreSettingsEntity)

    @Query("DELETE FROM store_settings")
    suspend fun deleteAll()
}
