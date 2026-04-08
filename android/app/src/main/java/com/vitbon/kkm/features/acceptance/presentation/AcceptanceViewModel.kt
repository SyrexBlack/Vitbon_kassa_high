package com.vitbon.kkm.features.acceptance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.DocumentDto
import com.vitbon.kkm.data.remote.dto.DocumentItemDto
import com.vitbon.kkm.features.products.domain.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AcceptanceState(
    val items: List<AcceptanceItem> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false
)

@HiltViewModel
class AcceptanceViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    private val api: VitbonApi
) : ViewModel() {

    private val _state = MutableStateFlow(AcceptanceState())
    val state: StateFlow<AcceptanceState> = _state.asStateFlow()

    fun addItem(barcode: String? = null, name: String = "Товар", price: Double = 0.0) {
        val item = AcceptanceItem(
            barcode = barcode ?: "TEST-${System.currentTimeMillis()}",
            name = name,
            quantity = 1.0,
            price = price
        )
        _state.update { it.copy(items = it.items + item) }
    }

    fun updateQuantity(barcode: String, quantity: Double) {
        _state.update { st ->
            st.copy(items = st.items.map {
                if (it.barcode == barcode) it.copy(quantity = quantity) else it
            })
        }
    }

    fun removeItem(barcode: String) {
        _state.update { st -> st.copy(items = st.items.filter { it.barcode != barcode }) }
    }

    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                val dto = DocumentDto(
                    type = "ACCEPTANCE",
                    items = _state.value.items.map {
                        DocumentItemDto(
                            productId = null,
                            barcode = it.barcode,
                            name = it.name,
                            quantity = it.quantity
                        )
                    },
                    timestamp = System.currentTimeMillis()
                )
                val response = api.sendAcceptance(dto)
                if (response.isSuccessful) {
                    _state.update { it.copy(isSubmitting = false, submitted = true, items = emptyList()) }
                } else {
                    _state.update { it.copy(isSubmitting = false, error = "Ошибка отправки: ${response.code()}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
