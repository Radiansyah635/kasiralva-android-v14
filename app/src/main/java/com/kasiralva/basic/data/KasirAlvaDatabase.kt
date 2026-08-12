package com.kasiralva.basic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        TransactionEntity::class,
        PurchaseEntity::class,
        StockOpnameEntity::class,
        StoreSettingsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class KasirAlvaDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockOpnameDao(): StockOpnameDao
    abstract fun storeSettingsDao(): StoreSettingsDao

    companion object {
        @Volatile private var INSTANCE: KasirAlvaDatabase? = null

        fun get(context: Context): KasirAlvaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KasirAlvaDatabase::class.java,
                    "kasiralva.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
