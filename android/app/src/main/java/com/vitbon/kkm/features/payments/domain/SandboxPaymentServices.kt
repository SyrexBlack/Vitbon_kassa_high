package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SandboxBankTerminalService @Inject constructor() : BankTerminalService {
    override suspend fun approvePayment(amount: Money, paymentType: PaymentType): TerminalResult {
        return TerminalResult.Success(approvalCode = "SANDBOX-${paymentType.value.uppercase()}-${amount.kopecks}")
    }
}

@Singleton
class SandboxSbpPaymentService @Inject constructor() : SbpPaymentService {
    override suspend fun createQr(amount: Money): SbpQrState {
        val transactionId = "SANDBOX-SBP-${amount.kopecks}"
        return SbpQrState.Created(
            qrData = "https://qr.sbp.nspk.ru/sandbox/$transactionId",
            transactionId = transactionId,
            expiresAt = System.currentTimeMillis() + 300_000L
        )
    }

    override suspend fun pollConfirmation(transactionId: String): SbpResult {
        return SbpResult.Confirmed(transactionId = transactionId)
    }

    override suspend fun cancelQr(transactionId: String): Boolean = true
}
