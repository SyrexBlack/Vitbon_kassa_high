package com.vitbon.kkm.features.correction.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionUseCase @Inject constructor(private val fiscalCore: FiscalCore) {
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
        return when (val result = fiscalCore.printCorrection(doc)) {
            is FiscalResult.Success -> CorrectionResult.Success(result.fiscalSign)
            is FiscalResult.Error -> CorrectionResult.Error(result.code, result.message)
        }
    }
}

sealed class CorrectionResult {
    data class Success(val fiscalSign: String) : CorrectionResult()
    data class Error(val code: Int, val message: String) : CorrectionResult()
}
