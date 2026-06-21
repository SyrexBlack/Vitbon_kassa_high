package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.sales.domain.Cart
import com.vitbon.kkm.features.sales.domain.SaleResult
import javax.inject.Inject
import javax.inject.Singleton

interface SalePaymentPipeline {
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: CashierRole?,
        emergencySessionActive: Boolean
    ): SaleResult
}

@Singleton
class DefaultSalePaymentPipeline @Inject constructor(
    private val terminalSaleUseCase: SaleWithTerminalUseCase,
    private val sbpSaleUseCase: SaleWithSbpUseCase
) : SalePaymentPipeline {
    override suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: CashierRole?,
        emergencySessionActive: Boolean
    ): SaleResult {
        return when (cart.paymentType) {
            PaymentType.SBP -> sbpSaleUseCase.execute(
                cart = cart,
                cashierId = cashierId,
                deviceId = deviceId,
                shiftId = shiftId,
                cashierRole = cashierRole,
                emergencySessionActive = emergencySessionActive
            )
            PaymentType.CASH,
            PaymentType.CARD,
            PaymentType.BONUS,
            PaymentType.MIXED -> terminalSaleUseCase.execute(
                cart = cart,
                cashierId = cashierId,
                deviceId = deviceId,
                shiftId = shiftId,
                cashierRole = cashierRole,
                emergencySessionActive = emergencySessionActive
            )
        }
    }
}
