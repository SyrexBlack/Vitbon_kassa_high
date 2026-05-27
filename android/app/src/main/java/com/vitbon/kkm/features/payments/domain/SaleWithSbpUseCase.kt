package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.sales.domain.Cart
import com.vitbon.kkm.features.sales.domain.MarkedGoodsSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val SBP_POLL_INTERVAL_MS = 2000L
private const val SBP_TIMEOUT_MS = 300_000L // 5 minutes

/**
 * Sale flow with SBP QR payment:
 * 1. Create QR → show to customer → poll for confirmation
 * 2. Fiscal sale only after SBP confirmation succeeds
 * 3. Unpaid/expired/timed-out/cancelled → no fiscal sale, surface error
 */
@Singleton
class SaleWithSbpUseCase @Inject constructor(
    private val sbpService: SbpPaymentService,
    private val innerUseCase: MarkedGoodsSaleUseCase
) {
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: CashierRole?,
        emergencySessionActive: Boolean,
        onQrCreated: (qrData: String, transactionId: String) -> Unit = { _, _ -> }
    ): SaleResult {
        if (cart.paymentType != PaymentType.SBP) {
            // Not SBP — delegate to regular terminal flow
            return innerUseCase.execute(cart, cashierId, deviceId, shiftId, cashierRole, emergencySessionActive)
        }

        // Step 1: create QR
        val qrState = sbpService.createQr(cart.total)
        val transactionId = when (qrState) {
            is SbpQrState.Created -> {
                onQrCreated(qrState.qrData, qrState.transactionId)
                qrState.transactionId
            }
            is SbpQrState.Expired -> return SaleResult.TerminalError(
                reason = "SBP_QR_EXPIRED",
                message = "QR-код СБП истёк"
            )
            is SbpQrState.Failed -> return SaleResult.TerminalError(
                reason = "SBP_QR_FAILED",
                message = qrState.reason
            )
            else -> return SaleResult.TerminalError(
                reason = "SBP_QR_INVALID",
                message = "QR-код не создан"
            )
        }

        // Step 2: poll for confirmation
        val confirmResult = pollSbpConfirmation(transactionId)

        return when (confirmResult) {
            is SbpResult.Confirmed -> {
                // Step 3: proceed to fiscal
                innerUseCase.execute(cart, cashierId, deviceId, shiftId, cashierRole, emergencySessionActive)
            }
            is SbpResult.Declined -> SaleResult.TerminalError(
                reason = "SBP_DECLINED",
                message = confirmResult.reason
            )
            is SbpResult.Timeout -> {
                sbpService.cancelQr(transactionId)
                SaleResult.TerminalError(
                    reason = "SBP_TIMEOUT",
                    message = "Оплата СБП не поступила в течение 5 минут"
                )
            }
            is SbpResult.CommunicationError -> SaleResult.TerminalError(
                reason = "SBP_COMM_ERROR",
                message = confirmResult.message
            )
        }
    }

    private suspend fun pollSbpConfirmation(transactionId: String): SbpResult {
        val deadline = System.currentTimeMillis() + SBP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            when (val result = sbpService.pollConfirmation(transactionId)) {
                is SbpResult.Confirmed -> return result
                is SbpResult.Declined -> return result
                is SbpResult.CommunicationError -> return result
                is SbpResult.Timeout -> return result
            }
            delay(SBP_POLL_INTERVAL_MS)
        }
        return SbpResult.Timeout
    }
}