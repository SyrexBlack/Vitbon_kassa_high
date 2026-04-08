package com.vitbon.kkm.features.products.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.products.data.ProductMapper
import com.vitbon.kkm.features.products.domain.Product
import com.vitbon.kkm.features.products.domain.ProductRepository
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
    private val repository: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProductsState())
    val state: StateFlow<ProductsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll()
                .map { entities -> entities.map { ProductMapper.entityToDomain(it) } }
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
                    .map { entities -> entities.map { ProductMapper.entityToDomain(it) } }
                    .first().let { products ->
                        _state.update { it.copy(products = products) }
                    }
            } else {
                repository.search(query).let { results ->
                    _state.update { it.copy(products = results.map { ProductMapper.entityToDomain(it) }) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Trigger sync from server
            _state.update { it.copy(isLoading = false) }
        }
    }
}
