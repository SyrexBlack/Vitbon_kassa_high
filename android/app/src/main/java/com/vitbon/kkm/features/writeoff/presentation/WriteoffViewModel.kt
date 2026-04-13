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

data class WriteoffItem(
    val id: Long,
    val barcode: String,
    val name: String,
    val quantity: Double
)

data class WriteoffState(
    val items: List<WriteoffItem> = emptyList(),
    val reason: String = "",
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WriteoffViewModel @Inject constructor(private val api: VitbonApi) : ViewModel() {
    private val _state = MutableStateFlow(WriteoffState())
    val state: StateFlow<WriteoffState> = _state.asStateFlow()
    private var nextItemId: Long = 1L

    fun addItem(barcode: String, name: String, quantity: Double) {
        val item = WriteoffItem(id = nextItemId++, barcode = barcode, name = name, quantity = quantity)
        _state.update { it.copy(items = it.items + item, submitted = false, error = null) }
    }

    fun remove(itemId: Long) {
        _state.update {
            it.copy(
                items = it.items.filter { i -> i.id != itemId },
                submitted = false,
                error = null
            )
        }
    }

    fun setReason(reason: String) {
        _state.update { it.copy(reason = reason, submitted = false, error = null) }
    }

    fun submit() {
        viewModelScope.launch {
            val current = _state.value
            if (current.items.isEmpty()) {
                _state.update {
                    it.copy(
                        submitting = false,
                        submitted = false,
                        error = "Добавьте хотя бы один товар"
                    )
                }
                return@launch
            }
            if (current.reason.isBlank()) {
                _state.update {
                    it.copy(
                        submitting = false,
                        submitted = false,
                        error = "Укажите причину списания"
                    )
                }
                return@launch
            }

            _state.update { it.copy(submitting = true, submitted = false, error = null) }
            try {
                val snapshot = _state.value
                val dto = DocumentDto(
                    type = "WRITEOFF",
                    items = snapshot.items.map {
                        DocumentItemDto(
                            productId = null,
                            barcode = it.barcode,
                            name = it.name,
                            quantity = it.quantity,
                            reason = snapshot.reason
                        )
                    },
                    timestamp = System.currentTimeMillis()
                )
                val response = api.sendWriteoff(dto)
                if (response.isSuccessful) {
                    _state.update {
                        WriteoffState(
                            submitted = true
                        )
                    }
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
