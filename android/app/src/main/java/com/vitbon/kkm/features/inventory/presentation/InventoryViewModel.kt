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

    fun setActual(barcode: String, actual: Double) {
        _state.update { st ->
            st.copy(items = st.items.map {
                if (it.barcode == barcode) it.copy(actual = actual) else it
            })
        }
    }

    fun submit() {
        viewModelScope.launch {
            val dto = DocumentDto(
                type = "INVENTORY",
                items = _state.value.items.map {
                    DocumentItemDto(productId = null, barcode = it.barcode, name = it.name,
                        quantity = it.actual - it.accounted)
                },
                timestamp = System.currentTimeMillis()
            )
            api.sendInventory(dto)
            _state.update { it.copy(submitted = true) }
        }
    }
}
