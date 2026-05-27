package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType

sealed class TerminalResult {
    data class Success(val approvalCode: String) : TerminalResult()
    data class Declined(val reason: String) : TerminalResult()
    data object Timeout : TerminalResult()
    data class CommunicationError(val message: String) : TerminalResult()
}

interface BankTerminalService {
    suspend fun approvePayment(amount: Money, paymentType: PaymentType): TerminalResult
}
