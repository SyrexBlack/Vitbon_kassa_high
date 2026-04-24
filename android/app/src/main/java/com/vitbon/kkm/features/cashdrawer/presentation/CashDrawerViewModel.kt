package com.vitbon.kkm.features.cashdrawer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.RolePolicy
import com.vitbon.kkm.features.cashdrawer.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CashDrawerState(
    val type: String = "in",
    val amount: String = "",
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val success: String? = null,
    val error: String? = null
)

@HiltViewModel
class CashDrawerViewModel @Inject constructor(
    private val useCase: CashDrawerUseCase,
    private val authUseCase: AuthUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(CashDrawerState())
    val state: StateFlow<CashDrawerState> = _state.asStateFlow()

    fun setType(t: String) { _state.update { it.copy(type = t, success = null, error = null) } }
    fun setAmount(a: String) { _state.update { it.copy(amount = a) } }
    fun setComment(c: String) { _state.update { it.copy(comment = c) } }

    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, success = null) }
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()
            val isCashIn = _state.value.type == "in"
            val allowed = if (isCashIn) useCase.canCashIn(role) else useCase.canCashOut(role)
            if (emergencyActive || !allowed) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = RolePolicy.ACCESS_DENIED_MESSAGE
                    )
                }
                return@launch
            }

            val amount = Money.fromRubles(_state.value.amount.toDoubleOrNull() ?: 0.0)
            val comment = _state.value.comment.ifBlank { null }
            val result = if (isCashIn)
                useCase.cashIn(amount, comment)
            else
                useCase.cashOut(amount, comment)

            _state.update {
                it.copy(
                    isSubmitting = false,
                    success = when (result) {
                        is CashDrawerResult.Success -> "Готово. ФП: ${result.fiscalSign.take(8)}"
                        else -> null
                    },
                    error = when (result) {
                        is CashDrawerResult.Error -> "${result.code}: ${result.message}"
                        else -> null
                    }
                )
            }
        }
    }
}
