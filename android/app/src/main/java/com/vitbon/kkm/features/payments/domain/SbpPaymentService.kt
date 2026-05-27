package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType

sealed class SbpResult {
    data class Confirmed(val transactionId: String) : SbpResult()
    data class Declined(val reason: String) : SbpResult()
    data object Timeout : SbpResult()
    data class CommunicationError(val message: String) : SbpResult()
}

sealed class SbpQrState {
    data class Created(val qrData: String, val transactionId: String, val expiresAt: Long) : SbpQrState()
    data object AwaitingConfirmation : SbpQrState()
    data object Confirmed : SbpQrState()
    data class Expired(val transactionId: String) : SbpQrState()
    data class Failed(val reason: String) : SbpQrState()
}

interface SbpPaymentService {
    suspend fun createQr(amount: Money): SbpQrState
    suspend fun pollConfirmation(transactionId: String): SbpResult
    suspend fun cancelQr(transactionId: String): Boolean
}