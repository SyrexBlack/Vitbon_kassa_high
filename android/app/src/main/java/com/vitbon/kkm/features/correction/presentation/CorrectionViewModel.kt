package com.vitbon.kkm.features.correction.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.features.correction.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CorrectionState(
    val type: String = "income",
    val reason: String = "",
    val correctionNumber: String = "",
    val cashAmount: String = "",
    val cardAmount: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CorrectionViewModel @Inject constructor(private val useCase: CorrectionUseCase) : ViewModel() {
    private val _state = MutableStateFlow(CorrectionState())
    val state: StateFlow<CorrectionState> = _state.asStateFlow()

    fun setType(type: String) { _state.update { it.copy(type = type) } }
    fun setReason(reason: String) { _state.update { it.copy(reason = reason) } }
    fun setCorrectionNumber(n: String) { _state.update { it.copy(correctionNumber = n) } }
    fun setCashAmount(a: String) { _state.update { it.copy(cashAmount = a) } }
    fun setCardAmount(a: String) { _state.update { it.copy(cardAmount = a) } }

    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val cash = Money.fromRubles(_state.value.cashAmount.toDoubleOrNull() ?: 0.0)
            val card = Money.fromRubles(_state.value.cardAmount.toDoubleOrNull() ?: 0.0)
            val result = useCase.process(
                type = if (_state.value.type == "income") CheckType.CORRECTION_INCOME
                        else CheckType.CORRECTION_EXPENSE,
                reason = _state.value.reason,
                correctionNumber = _state.value.correctionNumber.ifBlank {
                    System.currentTimeMillis().toString()
                },
                cashAmount = cash,
                cardAmount = card,
                vatRate = VatRate.VAT_22,
                cashierId = "unknown"
            )
            _state.update {
                it.copy(
                    isSubmitting = false,
                    error = when (result) {
                        is CorrectionResult.Error -> "${result.code}: ${result.message}"
                        else -> null
                    }
                )
            }
        }
    }
}
