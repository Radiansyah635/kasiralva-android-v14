package com.kasiralva.basic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockOpnameDao {
    @Query("SELECT * FROM stock_opname ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StockOpnameEntity>>

    @Query("SELECT * FROM stock_opname ORDER BY createdAt DESC")
    suspend fun getAll(): List<StockOpnameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StockOpnameEntity>)

    @Query("DELETE FROM stock_opname")
    suspend fun deleteAll()
}
