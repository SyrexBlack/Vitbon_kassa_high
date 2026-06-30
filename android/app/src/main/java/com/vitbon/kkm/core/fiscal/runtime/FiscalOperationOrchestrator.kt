package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.CorrectionDoc
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import javax.inject.Inject
import javax.inject.Singleton

/** Поставщик данных кассира для фискальных документов (теги 1055 и 1018 ФФД). */
interface CashierNameProvider {
    fun getCashierNameAndInn(): Pair<String, String?> // name, inn
}

@Singleton
class FiscalOperationOrchestrator @Inject constructor(
    private val fiscalCore: FiscalCore,
    private val ffdResolver: FfdVersionResolver,
    private val ffdPolicyStore: FfdPolicyStore,
    private val rootRiskGuard: RootRiskGuard,
    private val cashierNameProvider: CashierNameProvider
) {

    private fun checkSecurity(): FiscalRuntimeResult? {
        val blockState = rootRiskGuard.getCurrentBlockingState()
        if (blockState is AppBlockingState.Blocked) {
            return FiscalRuntimeResult.Error(
                code = "SECURITY_BLOCKED",
                message = blockState.reason,
                recoverable = false
            )
        }
        return null
    }

    /**
     * Фиксирует версию ФФД в policy store после первого успешного fiscal-документа.
     * В дальнейшем версия ФФД неизменна — FFD lock.
     */
    private suspend fun lockFfdIfFirstDocument() {
        val state = ffdPolicyStore.read()
        if (!state.locked) {
            val currentVersion = ffdResolver.resolve(forceRefresh = true)
            ffdPolicyStore.saveResolved(
                version = currentVersion,
                source = "LOCKED_AFTER_FIRST_DOC",
                locked = true,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun executeSale(check: FiscalCheck): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        val warnings = buildShiftAgeWarnings()
        val (name, inn) = cashierNameProvider.getCashierNameAndInn()
        val result = executeWithFormatRetry(
            primary = { fiscalCore.printSale(check, name, inn) },
            warnings = warnings
        )
        if (result is FiscalRuntimeResult.Success) {
            lockFfdIfFirstDocument()
        }
        return result
    }

    suspend fun executeReturn(check: FiscalCheck): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        val warnings = buildShiftAgeWarnings()
        val (name, inn) = cashierNameProvider.getCashierNameAndInn()
        return executeWithFormatRetry(
            primary = { fiscalCore.printReturn(check, name, inn) },
            warnings = warnings
        )
    }

    suspend fun executeCorrection(doc: CorrectionDoc): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        val warnings = buildShiftAgeWarnings()
        val (name, inn) = cashierNameProvider.getCashierNameAndInn()
        return executeWithFormatRetry(
            primary = { fiscalCore.printCorrection(doc, name, inn) },
            warnings = warnings
        )
    }

    suspend fun executeCashIn(amount: Money, comment: String?): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        val warnings = buildShiftAgeWarnings()
        return executeWithFormatRetry(
            primary = { fiscalCore.cashIn(amount, comment) },
            warnings = warnings
        )
    }

    suspend fun executeCashOut(amount: Money, comment: String?): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        val warnings = buildShiftAgeWarnings()
        return executeWithFormatRetry(
            primary = { fiscalCore.cashOut(amount, comment) },
            warnings = warnings
        )
    }

    suspend fun executeOpenShift(): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        return executeWithFormatRetry(
            primary = { fiscalCore.openShift() },
            warnings = emptyList()
        )
    }

    suspend fun executeCloseShift(): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        return executeWithFormatRetry(
            primary = { fiscalCore.closeShift() },
            warnings = emptyList()
        )
    }

    suspend fun executeXReport(): FiscalRuntimeResult {
        checkSecurity()?.let { return it }
        return executeWithFormatRetry(
            primary = { fiscalCore.printXReport() },
            warnings = emptyList()
        )
    }

    suspend fun executeStatusCheck(): FiscalStatus {
        return fiscalCore.getStatus()
    }

    private suspend fun executeWithFormatRetry(
        primary: suspend () -> FiscalResult,
        warnings: List<String> = emptyList()
    ): FiscalRuntimeResult {
        return try {
            ffdResolver.resolve(forceRefresh = false)
            primary().toRuntimeSuccess(ffdResolver.resolve(forceRefresh = false), warnings)
        } catch (t: Throwable) {
            val mapped = FiscalErrorMapper.map(t)
            if (mapped.code != "FORMAT_INVALID") return mapped

            try {
                val reResolved = ffdResolver.resolve(forceRefresh = true)
                primary().toRuntimeSuccess(reResolved, warnings)
            } catch (retryThrowable: Throwable) {
                FiscalErrorMapper.map(retryThrowable)
            }
        }
    }

    private fun FiscalResult.toRuntimeSuccess(ffdVersion: String, warnings: List<String> = emptyList()): FiscalRuntimeResult {
        return when (this) {
            is FiscalResult.Success -> FiscalRuntimeResult.Success(
                fiscalSign = fiscalSign,
                fnNumber = fnNumber,
                fdNumber = fdNumber,
                ffdVersion = ffdVersion,
                warnings = warnings
            )
            is FiscalResult.Error -> FiscalRuntimeResult.Error(
                code = "FISCAL_ERROR",
                message = message,
                recoverable = recoverable
            )
        }
    }

    private suspend fun buildShiftAgeWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        try {
            val status = fiscalCore.getStatus()
            if (status.shiftAgeHours != null && status.shiftAgeHours > 24) {
                warnings.add("Смена открыта более ${status.shiftAgeHours}ч. Закройте смену.")
            }
        } catch (_: Throwable) {
            // ignore status errors — fiscal operation proceeds regardless
        }
        return warnings
    }
}
