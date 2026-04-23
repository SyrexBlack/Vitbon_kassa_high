package com.vitbon.kkm.features.cashdrawer.domain

import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashDrawerUseCase @Inject constructor(private val fiscalOrchestrator: FiscalOperationOrchestrator) {
    suspend fun cashIn(amount: Money, comment: String?): CashDrawerResult {
        return when (val r = fiscalOrchestrator.executeCashIn(amount, comment)) {
            is FiscalRuntimeResult.Success -> CashDrawerResult.Success(r.fiscalSign)
            is FiscalRuntimeResult.Error -> CashDrawerResult.Error(-1, r.message)
        }
    }

    suspend fun cashOut(amount: Money, comment: String?): CashDrawerResult {
        return when (val r = fiscalOrchestrator.executeCashOut(amount, comment)) {
            is FiscalRuntimeResult.Success -> CashDrawerResult.Success(r.fiscalSign)
            is FiscalRuntimeResult.Error -> CashDrawerResult.Error(-1, r.message)
        }
    }
}

sealed class CashDrawerResult {
    data class Success(val fiscalSign: String) : CashDrawerResult()
    data class Error(val code: Int, val message: String) : CashDrawerResult()
}
