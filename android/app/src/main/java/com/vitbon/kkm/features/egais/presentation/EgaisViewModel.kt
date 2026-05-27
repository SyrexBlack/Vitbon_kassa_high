package com.vitbon.kkm.features.egais.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.egais.domain.EgaisDoc
import com.vitbon.kkm.features.egais.domain.EgaisRepository
import com.vitbon.kkm.features.egais.domain.UtmStatus
import com.vitbon.kkm.features.egais.domain.isOperational
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EgaisScreenState(
    val documents: List<EgaisDoc> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class UtmUiState {
    data object NotConfigured : UtmUiState()
    data class Unreachable(val reason: String) : UtmUiState()
    data class AuthError(val message: String) : UtmUiState()
    data class Error(val message: String) : UtmUiState()
    data object Ready : UtmUiState()
    data object Checking : UtmUiState()
}

@HiltViewModel
class EgaisViewModel @Inject constructor(
    private val repository: EgaisRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EgaisScreenState())
    val state: StateFlow<EgaisScreenState> = _state.asStateFlow()

    private val _utmState = MutableStateFlow<UtmUiState>(UtmUiState.Checking)
    val utmState: StateFlow<UtmUiState> = _utmState.asStateFlow()

    fun checkUtmStatus() {
        viewModelScope.launch {
            _utmState.value = UtmUiState.Checking
            _utmState.value = when (val status = repository.getUtmStatus()) {
                is UtmStatus.Ready -> UtmUiState.Ready
                is UtmStatus.NotConfigured -> UtmUiState.NotConfigured
                is UtmStatus.Unreachable -> UtmUiState.Unreachable(status.reason)
                is UtmStatus.AuthError -> UtmUiState.AuthError(status.message)
                is UtmStatus.UnknownError -> UtmUiState.Error(status.message)
            }
        }
    }

    fun acceptWaybill(xml: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.acceptIncomingWaybill(xml)
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (result is com.vitbon.kkm.features.egais.domain.EgaisResult.Error)
                    result.message else null
            )
        }
    }

    fun sendTaraAct(checkId: String, barcode: String, volume: Double) {
        viewModelScope.launch {
            repository.sendTaraAct(checkId, barcode, volume)
        }
    }
}