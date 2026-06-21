package com.vitbon.kkm.features.sales.presentation

import com.vitbon.kkm.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.sync.SyncPrefs
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.payments.domain.SalePaymentPipeline
import com.vitbon.kkm.features.sales.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalesState(
    val cart: Cart = Cart(),
    val searchQuery: String = "",
    val isProcessing: Boolean = false,
    val saleResult: SaleResult? = null,
    val scanError: String? = null
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val scanBarcode: ScanBarcodeUseCase,
    private val processSale: SalePaymentPipeline,
    private val authUseCase: AuthUseCase,
    private val syncService: SyncService,
    private val shiftDao: ShiftDao,
    private val syncPrefs: SyncPrefs
) : ViewModel() {

    private val _state = MutableStateFlow(SalesState())
    val state: StateFlow<SalesState> = _state.asStateFlow()

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query, scanError = null) }
        if (query.length < 4) return  // минимальная длина ШК

        viewModelScope.launch {
            when (val result = scanBarcode.execute(query)) {
                is ScanResult.Found -> {
                    addItem(result.item)
                    _state.update { it.copy(searchQuery = "", scanError = null) }
                }
                is ScanResult.NotFound -> {
                    _state.update { it.copy(scanError = query) }
                }
            }
        }
    }

    /** Добавить тестовый товар напрямую в корзину (для отладки) */
    fun addTestProduct() {
        if (!BuildConfig.DEBUG) {
            return
        }

        val testItem = CartItem(
            productId = "product-demo-water-05",
            barcode = "4607001234567",
            name = "Вода 0.5л",
            quantity = 1.0,
            price = Money(12900),
            discount = Money.ZERO,
            vatRate = VatRate.NO_VAT
        )
        addItem(testItem)
        _state.update { it.copy(scanError = null) }
    }

    private fun addItem(item: CartItem) {
        _state.update { st ->
            val existing = st.cart.items.find { it.productId == item.productId }
            val newItems = if (existing != null) {
                st.cart.items.map {
                    if (it.productId == item.productId) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                st.cart.items + item
            }
            st.copy(cart = st.cart.copy(items = newItems))
        }
    }

    fun updateQuantity(productId: String, quantity: Double) {
        if (quantity <= 0) {
            removeItem(productId)
            return
        }
        _state.update { st ->
            st.copy(cart = st.cart.copy(
                items = st.cart.items.map {
                    if (it.productId == productId) it.copy(quantity = quantity) else it
                }
            ))
        }
    }

    fun removeItem(productId: String) {
        _state.update { st ->
            st.copy(cart = st.cart.copy(items = st.cart.items.filter { it.productId != productId }))
        }
    }

    fun setPayment(type: PaymentType) {
        _state.update { st ->
            st.copy(cart = st.cart.copy(paymentType = type))
        }
    }

    fun applyGlobalDiscount(percent: Int) {
        val discount = _state.value.cart.subtotal * (percent / 100.0)
        _state.update { st ->
            st.copy(cart = st.cart.copy(globalDiscount = discount))
        }
    }

    fun processSale() {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, saleResult = null) }
            val role = authUseCase.getCurrentCashierRole()
            val emergencyActive = authUseCase.isEmergencySessionActive()
            if (emergencyActive) {
                authUseCase.auditEmergencyOperationDenied("SALE")
            }

            val cashierId = authUseCase.getCurrentCashierId() ?: "unknown"
            val openShiftId = shiftDao.findOpenShift()?.id
            val deviceId = syncPrefs.deviceId ?: "unknown-device"
            val result = processSale.execute(
                cart = _state.value.cart,
                cashierId = cashierId,
                deviceId = deviceId,
                shiftId = openShiftId,
                cashierRole = role,
                emergencySessionActive = emergencyActive
            )

            if (result is SaleResult.Success) {
                syncService.onCheckCreated()
            }

            _state.update { it.copy(isProcessing = false, saleResult = result) }
        }
    }

    fun clearCart() {
        _state.update { SalesState() }
    }
}
