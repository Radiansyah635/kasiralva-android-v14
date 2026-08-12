package com.kasiralva.basic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_opname")
data class StockOpnameEntity(
    @PrimaryKey val id: String,
    val date: String = "",
    val productId: String = "",
    val productName: String = "",
    val systemStock: Double = 0.0,
    val physicalStock: Double = 0.0,
    val difference: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
