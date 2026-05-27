package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.sales.domain.Cart
import com.vitbon.kkm.features.sales.domain.MarkedGoodsSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleWithTerminalUseCase @Inject constructor(
    private val terminalService: BankTerminalService,
    private val innerUseCase: MarkedGoodsSaleUseCase
) {
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: CashierRole?,
        emergencySessionActive: Boolean
    ): SaleResult {
        if (cart.paymentType != PaymentType.CASH) {
            val terminalResult = terminalService.approvePayment(cart.total, cart.paymentType)
            when (terminalResult) {
                is TerminalResult.Success -> { /* proceed to fiscal */ }
                is TerminalResult.Declined -> return SaleResult.TerminalError(
                    reason = terminalResult.reason,
                    message = "Terminal declined: ${terminalResult.reason}"
                )
                is TerminalResult.Timeout -> return SaleResult.TerminalError(
                    reason = "TIMEOUT",
                    message = "Terminal operation timed out"
                )
                is TerminalResult.CommunicationError -> return SaleResult.TerminalError(
                    reason = "COMMUNICATION_ERROR",
                    message = terminalResult.message
                )
            }
        }
        return innerUseCase.execute(cart, cashierId, deviceId, shiftId, cashierRole, emergencySessionActive)
    }
}