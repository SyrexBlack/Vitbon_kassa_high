package com.vitbon.kkm.features.egais.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.AgeVerificationResult
import com.vitbon.kkm.features.egais.domain.AgeVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgeVerificationState(
    val input: String = "",
    val isLoading: Boolean = false,
    val result: AgeVerificationResult? = null,
    val error: String? = null
)

@HiltViewModel
class AgeVerificationViewModel @Inject constructor(
    private val useCase: AgeVerificationUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(AgeVerificationState())
    val state: StateFlow<AgeVerificationState> = _state.asStateFlow()

    fun onInput(input: String) {
        _state.update { it.copy(input = input, error = null) }
    }

    fun verify() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = useCase.verify(_state.value.input)
            _state.update {
                it.copy(
                    isLoading = false,
                    result = result,
                    error = result.errorMessage
                )
            }
        }
    }
}
