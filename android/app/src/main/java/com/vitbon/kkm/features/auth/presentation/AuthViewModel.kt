package com.vitbon.kkm.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.auth.domain.AuthResult
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val pin: String = "",
    val isLoading: Boolean = false,
    val result: AuthResult? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authUseCase: AuthUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun appendDigit(digit: String) {
        if (_state.value.pin.length >= 6) return
        _state.update { it.copy(pin = it.pin + digit, result = null) }

        if (_state.value.pin.length >= 4) {
            attemptLogin()
        }
    }

    fun deleteLast() {
        _state.update { it.copy(pin = it.pin.dropLast(1), result = null) }
    }

    private fun attemptLogin() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = authUseCase.authenticate(_state.value.pin)
            _state.update { it.copy(isLoading = false, result = result) }
        }
    }

    fun reset() {
        _state.update { it.copy(pin = "", result = null) }
    }
}
