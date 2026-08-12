package com.kasiralva.basic.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {
    fun observeProducts(): Flow<List<ProductEntity>> = dao.observeAll()

    suspend fun save(product: ProductEntity) = dao.upsert(product)

    suspend fun delete(product: ProductEntity) = dao.delete(product)

    suspend fun changeStock(id: String, delta: Double) =
        dao.changeStock(id, delta)
}
