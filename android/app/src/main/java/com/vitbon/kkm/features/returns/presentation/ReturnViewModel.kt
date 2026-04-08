package com.vitbon.kkm.features.returns.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.returns.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReturnState(
    val checkInput: String = "",
    val originalCheck: LocalCheck? = null,
    val returnItems: List<SelectableReturnItem> = emptyList(),
    val returnTotal: Money = Money.ZERO,
    val isProcessing: Boolean = false,
    val error: String? = null
)

data class SelectableReturnItem(
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Money,
    val vatRate: VatRate,
    val selected: Boolean = true
)

@HiltViewModel
class ReturnViewModel @Inject constructor(
    private val returnUseCase: ReturnUseCase,
    private val authUseCase: AuthUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ReturnState())
    val state: StateFlow<ReturnState> = _state.asStateFlow()

    fun onCheckInput(input: String) {
        _state.update { it.copy(checkInput = input, error = null) }
        if (input.length >= 8) {
            viewModelScope.launch {
                val check = returnUseCase.findCheckByNumber(input)
                if (check != null) {
                    loadCheckItems(check)
                }
            }
        }
    }

    private suspend fun loadCheckItems(check: LocalCheck) {
        // В реальности: загрузить items из чека
        _state.update {
            it.copy(
                originalCheck = check,
                returnItems = listOf(
                    SelectableReturnItem("p1", "4601", "Товар", 2.0, Money(100_00L), VatRate.VAT_22)
                )
            )
        }
        recalcTotal()
    }

    fun toggleItem(name: String) {
        _state.update { st ->
            st.copy(returnItems = st.returnItems.map {
                if (it.name == name) it.copy(selected = !it.selected) else it
            })
        }
        recalcTotal()
    }

    private fun recalcTotal() {
        val total = Money(_state.value.returnItems.filter { it.selected }
            .sumOf { it.price.kopecks * it.quantity })
        _state.update { it.copy(returnTotal = total) }
    }

    fun processReturn() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            val check = _state.value.originalCheck ?: return@launch
            val items = _state.value.returnItems.filter { it.selected }.map {
                ReturnItem(it.productId, it.barcode, it.name, it.quantity, it.price, Money.ZERO, it.vatRate)
            }
            val result = returnUseCase.processReturn(check, items, authUseCase.getCurrentCashierId() ?: "unknown")
            _state.update {
                it.copy(
                    isProcessing = false,
                    error = when (result) {
                        is ReturnResult.FiscalError -> "${result.code}: ${result.message}"
                        else -> null
                    }
                )
            }
        }
    }
}
