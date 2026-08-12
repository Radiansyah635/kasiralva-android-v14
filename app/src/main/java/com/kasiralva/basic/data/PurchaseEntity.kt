package com.kasiralva.basic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val invoiceNo: String,
    val date: String,
    val supplier: String = "",
    val total: Long = 0,
    val itemsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)
