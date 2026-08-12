package com.kasiralva.basic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val receiptNo: String,
    val date: String,
    val subtotal: Long = 0,
    val discount: Long = 0,
    val total: Long = 0,
    val paymentMethod: String = "CASH",
    val paid: Long = 0,
    val changeAmount: Long = 0,
    val itemsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)
