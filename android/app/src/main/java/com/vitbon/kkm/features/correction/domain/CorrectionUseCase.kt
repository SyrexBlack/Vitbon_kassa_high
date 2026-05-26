package com.vitbon.kkm.features.correction.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.auth.domain.RolePolicy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionUseCase @Inject constructor(
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val authUseCase: AuthUseCase,
    private val fiscalConfig: FiscalConfig
) {
    suspend fun process(
        type: CheckType,  // CORRECTION_INCOME or CORRECTION_EXPENSE
        reason: String,
        correctionNumber: String,
        cashAmount: Money,
        cardAmount: Money,
        vatRate: VatRate,
        cashierId: String
    ): CorrectionResult {
        val role = authUseCase.getCurrentCashierRole()
        if (role != CashierRole.ADMIN) {
            return CorrectionResult.Error(-1, RolePolicy.ACCESS_DENIED_MESSAGE)
        }

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
            vatRate = vatRate,
            taxSystem = fiscalConfig.taxSystem
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
