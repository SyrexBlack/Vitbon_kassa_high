package com.vitbon.kkm.core.fiscal.msposk

import android.content.Context
import android.util.Log
import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над MSPOS-K SDK для фискализации.
 * Реализует FiscalCore через команды MSPOS-K API.
 *
 * SDK поставляется производителем как .aar library.
 * Здесь — обёртка; заменить stub-вызовы на реальные вызовы SDK после интеграции.
 */
@Singleton
class MSPOSKFiscalCore @Inject constructor(
    private val context: Context
) : FiscalCore {

    private val TAG = "MSPOSKFiscalCore"

    // Инициализирован ли SDK
    @Volatile
    private var initialized = false

    // Кэш состояния ФН
    @Volatile
    private var cachedStatus: FiscalStatus? = null

    private val sdk: MSPOSKProtocol by lazy {
        RealMSPOSKProtocol(context)
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing MSPOS-K SDK...")
            // sdk.initialize(context) — вызов SDK производителя
            initialized = true
            Log.d(TAG, "MSPOS-K SDK initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MSPOS-K SDK", e)
            false
        }
    }

    override suspend fun shutdown(): Unit = withContext(Dispatchers.IO) {
        initialized = false
        // sdk.shutdown() — вызов SDK
        Log.d(TAG, "MSPOS-K SDK shut down")
    }

    override suspend fun openShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Opening shift...")
        sdk.openShift()
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing SALE check ${check.id}")
        require(check.type == CheckType.SALE) { "Expected SALE, got ${check.type}" }
        sdk.printSale(check)
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing RETURN check ${check.id}")
        require(check.type == CheckType.RETURN) { "Expected RETURN, got ${check.type}" }
        sdk.printReturn(check)
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing CORRECTION ${doc.id}")
        sdk.printCorrection(doc)
    }

    override suspend fun closeShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Closing shift...")
        cachedStatus = null
        sdk.closeShift()
    }

    override suspend fun printXReport(): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing X-report...")
        sdk.printXReport()
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash in: ${amount.rubles}₽")
        sdk.cashIn(amount, comment)
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash out: ${amount.rubles}₽")
        sdk.cashOut(amount, comment)
    }

    override suspend fun getStatus(): FiscalStatus = withContext(Dispatchers.IO) {
        cachedStatus?.let { return@withContext it }
        sdk.getStatus().also { cachedStatus = it }
    }

    override suspend fun getFFDVersion(): FFDVersion = withContext(Dispatchers.IO) {
        sdk.getFFDVersion()
    }

    /**
     * Оборачивает вызов SDK в обработку ошибок.
     * recoverable = true при transient errors (timeout, busy)
     */
    private suspend fun <T> executeFiscal(block: suspend () -> T): T {
        check(initialized) { "SDK not initialized. Call initialize() first." }
        return try {
            withContext(Dispatchers.IO) {
                block()
            }
        } catch (e: FiscalException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fiscal operation failed", e)
            throw FiscalException(
                errorCode = -1,
                message = e.message ?: "Unknown error",
                recoverable = e is java.util.concurrent.TimeoutException
            )
        }
    }
}

interface MSPOSKProtocol {
    suspend fun openShift(): FiscalResult
    suspend fun printSale(check: FiscalCheck): FiscalResult
    suspend fun printReturn(check: FiscalCheck): FiscalResult
    suspend fun printCorrection(doc: CorrectionDoc): FiscalResult
    suspend fun closeShift(): FiscalResult
    suspend fun printXReport(): FiscalResult
    suspend fun cashIn(amount: Money, comment: String?): FiscalResult
    suspend fun cashOut(amount: Money, comment: String?): FiscalResult
    suspend fun getStatus(): FiscalStatus
    suspend fun getFFDVersion(): FFDVersion
}

class RealMSPOSKProtocol(private val context: Context) : MSPOSKProtocol {
    override suspend fun openShift(): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun printSale(check: FiscalCheck): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun printReturn(check: FiscalCheck): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun closeShift(): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun printXReport(): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun getStatus(): FiscalStatus = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
    override suspend fun getFFDVersion(): FFDVersion = throw UnsupportedOperationException("MSPOS-K SDK integration is required")
}
