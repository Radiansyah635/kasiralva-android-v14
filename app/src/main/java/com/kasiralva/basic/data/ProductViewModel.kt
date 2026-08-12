package com.kasiralva.basic.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductViewModel(private val repository: ProductRepository) : ViewModel() {
    val products: StateFlow<List<ProductEntity>> =
        repository.observeProducts().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun save(product: ProductEntity) {
        viewModelScope.launch { repository.save(product) }
    }

    fun delete(product: ProductEntity) {
        viewModelScope.launch { repository.delete(product) }
    }

    fun changeStock(id: String, delta: Double) {
        viewModelScope.launch { repository.changeStock(id, delta) }
    }
}

class ProductViewModelFactory(
    private val repository: ProductRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {
            return ProductViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
