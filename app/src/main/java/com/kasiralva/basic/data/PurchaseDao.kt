package com.kasiralva.basic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    suspend fun getAll(): List<PurchaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PurchaseEntity>)

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
}
