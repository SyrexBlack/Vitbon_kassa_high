package com.vitbon.kkm.features.products.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.features.products.domain.Product
import com.vitbon.kkm.features.products.domain.ProductRepository
import com.vitbon.kkm.features.products.domain.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductsState(
    val products: List<Product> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val syncService: SyncService
) : ViewModel() {

    private val _state = MutableStateFlow(ProductsState())
    val state: StateFlow<ProductsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll()
                .collect { products ->
                    _state.update { it.copy(products = products, isLoading = false) }
                }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                repository.observeAll()
                    .first().let { products ->
                        _state.update { it.copy(products = products) }
                    }
            } else {
                repository.search(query).let { results ->
                    _state.update { it.copy(products = results.map { it.toDomain() }) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                syncService.syncProductsNow()
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
