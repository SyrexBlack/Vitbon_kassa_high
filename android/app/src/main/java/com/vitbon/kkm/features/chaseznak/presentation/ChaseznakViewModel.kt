package com.vitbon.kkm.features.chaseznak.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.chaseznak.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChaseznakState(
    val scanInput: String = "",
    val items: List<ChaseznakValidatedItem> = emptyList(),
    val isValidating: Boolean = false,
    val isSelling: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChaseznakViewModel @Inject constructor(
    private val repository: ChaseznakRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ChaseznakState())
    val state: StateFlow<ChaseznakState> = _state.asStateFlow()

    fun onScan(code: String) {
        _state.update { it.copy(scanInput = code, isValidating = true) }
        if (code.length < 20) return

        viewModelScope.launch {
            val validation = repository.validateCode(code)
            _state.update {
                it.copy(
                    isValidating = false,
                    items = it.items + ChaseznakValidatedItem(
                        code = code,
                        status = validation.status,
                        productName = validation.productName
                    ),
                    scanInput = ""
                )
            }
        }
    }

    fun remove(code: String) {
        _state.update { it.copy(items = it.items.filter { i -> i.code != code }) }
    }

    fun sellAll(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSelling = true) }
            val okItems = _state.value.items.filter { it.status == ChaseznakStatus.OK }
            for (item in okItems) {
                repository.sell(item.code, "LOCAL-${System.currentTimeMillis()}")
            }
            _state.update { ChaseznakState() }
            onComplete()
        }
    }
}
