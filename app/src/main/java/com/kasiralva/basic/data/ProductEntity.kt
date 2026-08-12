package com.kasiralva.basic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val barcode: String = "",
    val name: String = "",
    val category: String = "",
    val buyPrice: Long = 0,
    val sellPrice: Long = 0,
    val stock: Double = 0.0,
    val minStock: Double = 0.0,
    val unit: String = "pcs",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
