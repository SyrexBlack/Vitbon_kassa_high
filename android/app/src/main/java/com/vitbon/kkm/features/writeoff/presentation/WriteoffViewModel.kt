package com.vitbon.kkm.features.writeoff.presentation

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

data class WriteoffItem(val barcode: String, val name: String, val quantity: Double)
data class WriteoffState(val items: List<WriteoffItem> = emptyList(), val reason: String = "", val submitting: Boolean = false)

@HiltViewModel
class WriteoffViewModel @Inject constructor(private val api: VitbonApi) : ViewModel() {
    private val _state = MutableStateFlow(WriteoffState())
    val state: StateFlow<WriteoffState> = _state.asStateFlow()

    fun addItem(barcode: String, name: String, quantity: Double) {
        _state.update { it.copy(items = it.items + WriteoffItem(barcode, name, quantity)) }
    }

    fun remove(barcode: String) {
        _state.update { it.copy(items = it.items.filter { i -> i.barcode != barcode }) }
    }

    fun setReason(reason: String) {
        _state.update { it.copy(reason = reason) }
    }

    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            try {
                val dto = DocumentDto(
                    type = "WRITEOFF",
                    items = _state.value.items.map {
                        DocumentItemDto(productId = null, barcode = it.barcode, name = it.name,
                            quantity = it.quantity, reason = _state.value.reason)
                    },
                    timestamp = System.currentTimeMillis()
                )
                api.sendWriteoff(dto)
                _state.update { WriteoffState() }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false) }
            }
        }
    }
}
