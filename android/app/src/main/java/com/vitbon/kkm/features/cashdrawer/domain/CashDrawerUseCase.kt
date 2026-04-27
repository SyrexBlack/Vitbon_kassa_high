package com.vitbon.kkm.features.cashdrawer.domain

import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.auth.domain.RoleOperation
import com.vitbon.kkm.features.auth.domain.RolePolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashDrawerUseCase @Inject constructor(private val fiscalOrchestrator: FiscalOperationOrchestrator) {
    fun canCashIn(role: CashierRole?): Boolean = RolePolicy.canPerform(role, RoleOperation.CASH_IN)

    fun canCashOut(role: CashierRole?): Boolean = RolePolicy.canPerform(role, RoleOperation.CASH_OUT)

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
