package com.vitbon.kkm.features.returns.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.RolePolicy
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
    val itemKey: String,
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Money,
    val discount: Money,
    val vatRate: VatRate,
    val selected: Boolean = true
)

@HiltViewModel
class ReturnViewModel @Inject constructor(
    private val returnUseCase: ReturnUseCase,
    private val authUseCase: AuthUseCase,
    private val syncService: SyncService
) : ViewModel() {
    private val _state = MutableStateFlow(ReturnState())
    val state: StateFlow<ReturnState> = _state.asStateFlow()

    fun onCheckInput(input: String) {
        _state.update { it.copy(checkInput = input, error = null) }
        val normalizedInput = input.trim()

        if (normalizedInput.isEmpty()) {
            _state.update {
                it.copy(
                    originalCheck = null,
                    returnItems = emptyList(),
                    returnTotal = Money.ZERO,
                    error = null
                )
            }
            return
        }

        viewModelScope.launch {
            val isQrLike = looksLikeQrPayload(normalizedInput)
            val check = if (isQrLike) {
                returnUseCase.findCheckByQr(normalizedInput)
            } else {
                returnUseCase.findCheckByNumber(normalizedInput)
            }
            if (_state.value.checkInput.trim() != normalizedInput) return@launch

            if (check != null) {
                loadCheckItems(check, normalizedInput)
            } else {
                _state.update {
                    it.copy(
                        originalCheck = null,
                        returnItems = emptyList(),
                        returnTotal = Money.ZERO,
                        error = if (normalizedInput.length >= 8) "Чек продажи не найден" else null
                    )
                }
            }
        }
    }

    private suspend fun loadCheckItems(check: LocalCheck, expectedInput: String) {
        val items = returnUseCase.loadCheckItems(check.id).mapIndexed { index, item ->
            SelectableReturnItem(
                itemKey = "$index:${item.productId ?: item.barcode ?: item.name}:${item.quantity}:${item.price.kopecks}",
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                discount = item.discount,
                vatRate = item.vatRate
            )
        }

        if (_state.value.checkInput.trim() != expectedInput) return

        _state.update {
            it.copy(
                originalCheck = check,
                returnItems = items,
                error = if (items.isEmpty()) "Позиции исходного чека не найдены" else null
            )
        }
        recalcTotal()
    }

    fun toggleItem(itemKey: String) {
        _state.update { st ->
            st.copy(returnItems = st.returnItems.map {
                if (it.itemKey == itemKey) it.copy(selected = !it.selected) else it
            })
        }
        recalcTotal()
    }

    private fun recalcTotal() {
        val total = Money(_state.value.returnItems.filter { it.selected }
            .sumOf { (it.price.kopecks * it.quantity).toLong() - it.discount.kopecks })
        _state.update { it.copy(returnTotal = total) }
    }

    private fun looksLikeQrPayload(input: String): Boolean {
        val normalized = input.lowercase()
        val hasEquals = normalized.contains("=")
        val hasQrTokens = normalized.contains("fp=") ||
            normalized.contains("fn=") ||
            normalized.contains("t=") ||
            normalized.contains("i=") ||
            normalized.contains("s=")
        return hasEquals && hasQrTokens
    }

    fun processReturn() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, error = null) }
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()

            val check = _state.value.originalCheck
            if (check == null) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = "Сначала выберите исходный чек продажи"
                    )
                }
                return@launch
            }
            val items = _state.value.returnItems.filter { it.selected }.map {
                ReturnItem(it.productId, it.barcode, it.name, it.quantity, it.price, it.discount, it.vatRate)
            }
            val result = returnUseCase.processReturn(
                originalCheck = check,
                items = items,
                cashierId = authUseCase.getCurrentCashierId() ?: "unknown",
                cashierRole = role,
                emergencySessionActive = emergencyActive
            )
            if (result is ReturnResult.Success) {
                syncService.onCheckCreated()
            }
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
