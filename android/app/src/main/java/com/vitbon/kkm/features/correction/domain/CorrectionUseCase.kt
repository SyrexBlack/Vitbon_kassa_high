package com.vitbon.kkm.features.correction.domain

import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionUseCase @Inject constructor(private val fiscalOrchestrator: FiscalOperationOrchestrator) {
    suspend fun process(
        type: CheckType,  // CORRECTION_INCOME or CORRECTION_EXPENSE
        reason: String,
        correctionNumber: String,
        cashAmount: Money,
        cardAmount: Money,
        vatRate: VatRate,
        cashierId: String
    ): CorrectionResult {
        require(type == CheckType.CORRECTION_INCOME || type == CheckType.CORRECTION_EXPENSE) {
            "Invalid correction type"
        }
        val doc = CorrectionDoc(
            id = UUID.randomUUID().toString(),
            type = type,
            baseSum = cashAmount + cardAmount,
            cashSum = cashAmount,
            cardSum = cardAmount,
            reason = reason,
            correctionNumber = correctionNumber,
            correctionDate = System.currentTimeMillis(),
            vatRate = vatRate
        )
        return when (val result = fiscalOrchestrator.executeCorrection(doc)) {
            is FiscalRuntimeResult.Success -> CorrectionResult.Success(result.fiscalSign)
            is FiscalRuntimeResult.Error -> CorrectionResult.Error(-1, result.message)
        }
    }
}

sealed class CorrectionResult {
    data class Success(val fiscalSign: String) : CorrectionResult()
    data class Error(val code: Int, val message: String) : CorrectionResult()
}
