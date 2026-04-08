package com.vitbon.kkm.features.cashdrawer.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashDrawerUseCase @Inject constructor(private val fiscalCore: FiscalCore) {
    suspend fun cashIn(amount: Money, comment: String?): CashDrawerResult {
        return when (val r = fiscalCore.cashIn(amount, comment)) {
            is FiscalResult.Success -> CashDrawerResult.Success(r.fiscalSign)
            is FiscalResult.Error -> CashDrawerResult.Error(r.code, r.message)
        }
    }

    suspend fun cashOut(amount: Money, comment: String?): CashDrawerResult {
        return when (val r = fiscalCore.cashOut(amount, comment)) {
            is FiscalResult.Success -> CashDrawerResult.Success(r.fiscalSign)
            is FiscalResult.Error -> CashDrawerResult.Error(r.code, r.message)
        }
    }
}

sealed class CashDrawerResult {
    data class Success(val fiscalSign: String) : CashDrawerResult()
    data class Error(val code: Int, val message: String) : CashDrawerResult()
}
