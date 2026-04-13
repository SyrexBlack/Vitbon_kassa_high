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
    private var nextItemId: Long = 1L

    fun addItem(barcode: String? = null, name: String = "Товар", price: Double = 0.0) {
        val itemId = nextItemId++
        val item = AcceptanceItem(
            id = itemId,
            barcode = barcode ?: "TEST-$itemId",
            name = name,
            quantity = 1.0,
            price = price
        )
        _state.update { it.copy(items = it.items + item, error = null, submitted = false) }
    }

    fun updateQuantity(itemId: Long, quantity: Double) {
        _state.update { st ->
            st.copy(
                items = st.items.map {
                    if (it.id == itemId) it.copy(quantity = quantity) else it
                },
                error = null,
                submitted = false
            )
        }
    }

    fun removeItem(itemId: Long) {
        _state.update {
            it.copy(
                items = it.items.filter { item -> item.id != itemId },
                error = null,
                submitted = false
            )
        }
    }

    fun submit() {
        viewModelScope.launch {
            val itemsToSend = _state.value.items
            if (itemsToSend.isEmpty()) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitted = false,
                        error = "Добавьте хотя бы один товар"
                    )
                }
                return@launch
            }

            _state.update { it.copy(isSubmitting = true, error = null, submitted = false) }
            try {
                val dto = DocumentDto(
                    type = "ACCEPTANCE",
                    items = itemsToSend.map {
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
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = true,
                            error = null,
                            items = emptyList()
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = false,
                            error = "Ошибка отправки: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitted = false,
                        error = e.message ?: "Ошибка отправки"
                    )
                }
            }
        }
    }
}
