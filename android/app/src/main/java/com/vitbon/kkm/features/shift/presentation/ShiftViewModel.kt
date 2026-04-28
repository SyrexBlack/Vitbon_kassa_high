package com.vitbon.kkm.features.shift.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.RolePolicy
import com.vitbon.kkm.features.shift.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShiftState(
    val shiftStatus: ShiftStatus = ShiftStatus.CLOSED,
    val opened: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val useCase: ShiftUseCase,
    private val authUseCase: AuthUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ShiftState())
    val state: StateFlow<ShiftState> = _state.asStateFlow()

    fun checkStatus() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val status = useCase.checkShiftStatus()
            _state.update { it.copy(shiftStatus = status, isLoading = false) }
        }
    }

    fun openShift() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()
            if (emergencyActive || !useCase.canOpenShift(role)) {
                _state.update { it.copy(isLoading = false, error = RolePolicy.ACCESS_DENIED_MESSAGE) }
                return@launch
            }
            val result = useCase.openShift(
                deviceId = android.os.Build.MODEL,
                cashierId = authUseCase.getCurrentCashierId() ?: "unknown"
            )
            when (result) {
                is ShiftResult.Success -> _state.update { it.copy(shiftStatus = ShiftStatus.OPEN, opened = true, isLoading = false) }
                is ShiftResult.Error -> _state.update { it.copy(error = "${result.code}: ${result.message}", isLoading = false) }
            }
        }
    }

    fun closeShift() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()
            if (emergencyActive || !useCase.canCloseShift(role)) {
                _state.update { it.copy(isLoading = false, error = RolePolicy.ACCESS_DENIED_MESSAGE) }
                return@launch
            }
            val shiftId = useCase.findOpenShiftId()
            if (shiftId == null) {
                _state.update { it.copy(isLoading = false, error = "Нет открытой смены для закрытия") }
                return@launch
            }

            when (val result = useCase.closeShift(shiftId)) {
                is ShiftResult.Success -> {
                    _state.update {
                        it.copy(
                            shiftStatus = ShiftStatus.CLOSED,
                            opened = false,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is ShiftResult.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "${result.code}: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun printXReport() {
        viewModelScope.launch {
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()
            if (emergencyActive || !useCase.canPrintXReport(role)) {
                _state.update { it.copy(error = RolePolicy.ACCESS_DENIED_MESSAGE) }
                return@launch
            }
            useCase.printXReport()
            _state.update { it.copy(error = null) }
        }
    }
}
