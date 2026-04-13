package com.vitbon.kkm.features.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.DocumentDto
import com.vitbon.kkm.data.remote.dto.DocumentItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(private val api: VitbonApi) : ViewModel() {
    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    fun startInventory() {
        // Загрузить все товары из локальной базы
        // Пока: пустой список — подключить ProductRepository
    }

    fun setItemsForTest(items: List<InventoryItem>) {
        _state.update { it.copy(items = items, submitted = false, error = null) }
    }

    fun setActual(barcode: String, actual: Double) {
        _state.update { st ->
            st.copy(
                items = st.items.map {
                    if (it.barcode == barcode) it.copy(actual = actual) else it
                },
                submitted = false,
                error = null
            )
        }
    }

    fun submit() {
        viewModelScope.launch {
            val snapshot = _state.value
            if (snapshot.items.isEmpty()) {
                _state.update {
                    it.copy(
                        submitting = false,
                        submitted = false,
                        error = "Нет данных для инвентаризации"
                    )
                }
                return@launch
            }

            _state.update { it.copy(submitting = true, submitted = false, error = null) }
            try {
                val payload = _state.value.items
                val dto = DocumentDto(
                    type = "INVENTORY",
                    items = payload.map {
                        DocumentItemDto(
                            productId = null,
                            barcode = it.barcode,
                            name = it.name,
                            quantity = it.actual - it.accounted
                        )
                    },
                    timestamp = System.currentTimeMillis()
                )
                val response = api.sendInventory(dto)
                if (response.isSuccessful) {
                    _state.update { it.copy(submitting = false, submitted = true, error = null) }
                } else {
                    _state.update {
                        it.copy(
                            submitting = false,
                            submitted = false,
                            error = "Ошибка отправки: ${response.code()}"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        submitted = false,
                        error = e.message ?: "Ошибка отправки"
                    )
                }
            }
        }
    }
}
