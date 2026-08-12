package com.kasiralva.basic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_settings")
data class StoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val ownerPassword: String = "1234",
    val receiptFooter: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
