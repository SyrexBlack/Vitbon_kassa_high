package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.CorrectionDoc
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.Money
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FiscalOperationOrchestrator @Inject constructor(
    private val fiscalCore: FiscalCore,
    private val ffdResolver: FfdVersionResolver
) {
    suspend fun executeSale(check: FiscalCheck): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.printSale(check) }
        )
    }

    suspend fun executeReturn(check: FiscalCheck): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.printReturn(check) }
        )
    }

    suspend fun executeCorrection(doc: CorrectionDoc): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.printCorrection(doc) }
        )
    }

    suspend fun executeCashIn(amount: Money, comment: String?): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.cashIn(amount, comment) }
        )
    }

    suspend fun executeCashOut(amount: Money, comment: String?): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.cashOut(amount, comment) }
        )
    }

    suspend fun executeOpenShift(): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.openShift() }
        )
    }

    suspend fun executeCloseShift(): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.closeShift() }
        )
    }

    suspend fun executeXReport(): FiscalRuntimeResult {
        return executeWithFormatRetry(
            primary = { fiscalCore.printXReport() }
        )
    }

    private suspend fun executeWithFormatRetry(
        primary: suspend () -> FiscalResult
    ): FiscalRuntimeResult {
        return try {
            ffdResolver.resolve(forceRefresh = false)
            primary().toRuntimeSuccess(ffdResolver.resolve(forceRefresh = false))
        } catch (t: Throwable) {
            val mapped = FiscalErrorMapper.map(t)
            if (mapped.code != "FORMAT_INVALID") return mapped

            try {
                val reResolved = ffdResolver.resolve(forceRefresh = true)
                primary().toRuntimeSuccess(reResolved)
            } catch (retryThrowable: Throwable) {
                FiscalErrorMapper.map(retryThrowable)
            }
        }
    }

    private fun FiscalResult.toRuntimeSuccess(ffdVersion: String): FiscalRuntimeResult {
        return when (this) {
            is FiscalResult.Success -> FiscalRuntimeResult.Success(
                fiscalSign = fiscalSign,
                fnNumber = fnNumber,
                fdNumber = fdNumber,
                ffdVersion = ffdVersion
            )
            is FiscalResult.Error -> FiscalRuntimeResult.Error(
                code = "FISCAL_ERROR",
                message = message,
                recoverable = recoverable
            )
        }
    }
}
