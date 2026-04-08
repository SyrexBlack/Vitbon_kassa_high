package com.vitbon.kkm.features.egais.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.egais.domain.EgaisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EgaisState(
    val documents: List<com.vitbon.kkm.features.egais.domain.EgaisDoc> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class EgaisViewModel @Inject constructor(
    private val repository: EgaisRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EgaisState())
    val state: StateFlow<EgaisState> = _state.asStateFlow()

    private val _utmStatus = MutableStateFlow(false)
    val utmStatus: StateFlow<Boolean> = _utmStatus.asStateFlow()

    fun checkUtmStatus() {
        viewModelScope.launch {
            _utmStatus.value = repository.checkUtmAvailable()
        }
    }

    fun acceptWaybill(xml: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.acceptIncomingWaybill(xml)
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (result is com.vitbon.kkm.features.egais.domain.EgaisResult.Error)
                    (result as com.vitbon.kkm.features.egais.domain.EgaisResult.Error).message else null
            )
        }
    }

    fun sendTaraAct(checkId: String, barcode: String, volume: Double) {
        viewModelScope.launch {
            repository.sendTaraAct(checkId, barcode, volume)
        }
    }
}
