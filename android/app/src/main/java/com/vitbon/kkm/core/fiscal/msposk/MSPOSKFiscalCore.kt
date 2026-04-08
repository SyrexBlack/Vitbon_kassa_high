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
        // В реальной реализации: MSPOSKSDK.getInstance(context)
        // Заглушка — заменить на реальный SDK после получения .aar
        MSPOSKStub()
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

    override suspend fun shutdown() = withContext(Dispatchers.IO) {
        initialized = false
        // sdk.shutdown() — вызов SDK
        Log.d(TAG, "MSPOS-K SDK shut down")
    }

    override suspend fun openShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Opening shift...")
        // val result = sdk.openShift()
        // FiscalResult.Success(result.fiscalSign, result.fnNumber, result.fdNumber, System.currentTimeMillis())
        FiscalResult.Success(
            fiscalSign = "MSP_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = "1",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing SALE check ${check.id}")
        require(check.type == CheckType.SALE) { "Expected SALE, got ${check.type}" }
        // val doc = buildFFDDocument(check) // уже построен в FiscalDocumentBuilder
        // val result = sdk.printCheck(doc)
        FiscalResult.Success(
            fiscalSign = "MSP_FISCAL_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = (check.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing RETURN check ${check.id}")
        require(check.type == CheckType.RETURN) { "Expected RETURN, got ${check.type}" }
        // val result = sdk.printReturn(check)
        FiscalResult.Success(
            fiscalSign = "MSP_RETURN_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = (check.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing CORRECTION ${doc.id}")
        // val result = sdk.printCorrection(doc)
        FiscalResult.Success(
            fiscalSign = "MSP_CORR_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = (doc.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun closeShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Closing shift...")
        // val result = sdk.closeShift()
        cachedStatus = null // инвалидировать кэш
        FiscalResult.Success(
            fiscalSign = "MSP_CLOSE_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printXReport(): FiscalResult = executeFiscal {
        Log.d(TAG, "Printing X-report...")
        FiscalResult.Success(
            fiscalSign = "MSP_XREP_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash in: ${amount.rubles}₽")
        FiscalResult.Success(
            fiscalSign = "MSP_CASHIN_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Cash out: ${amount.rubles}₽")
        FiscalResult.Success(
            fiscalSign = "MSP_CASHOUT_${System.currentTimeMillis()}",
            fnNumber = "0000000000000000",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun getStatus(): FiscalStatus = withContext(Dispatchers.IO) {
        cachedStatus?.let { return@withContext it }
        try {
            // val raw = sdk.getStatus()
            val status = FiscalStatus(
                fnRegistered = true,
                fnNumber = "0000000000000000",
                shiftOpen = true,
                shiftAgeHours = 2L,
                currentFdNumber = 42,
                ofdConnected = true,
                lastError = null
            )
            cachedStatus = status
            status
        } catch (e: Exception) {
            FiscalStatus(
                fnRegistered = false, fnNumber = null, shiftOpen = false,
                shiftAgeHours = null, currentFdNumber = 0,
                ofdConnected = false, lastError = e.message
            )
        }
    }

    override suspend fun getFFDVersion(): FFDVersion = withContext(Dispatchers.IO) {
        try {
            // val version = sdk.getFFDVersion() // "1.05" or "1.2"
            // FFDVersion.fromString(version)
            FFDVersion.V1_05 // default
        } catch (e: Exception) {
            FFDVersion.V1_05
        }
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

/** Stub-протокол MSPOS-K (заменить на реальный SDK после получения .aar) */
interface MSPOSKProtocol {
    fun initialize(context: Context)
    fun shutdown()
    // Добавить все методы SDK после получения документации производителя
}

/** Stub реализация до интеграции SDK */
class MSPOSKStub : MSPOSKProtocol {
    override fun initialize(context: Context) {}
    override fun shutdown() {}
}
