package com.vitbon.kkm.core.fiscal.neva

import android.content.Context
import android.util.Log
import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над встроенным фискальным модулем Нева 01Ф.
 * Нева 01Ф имеет встроенный ФН — работает по протоколу производителя.
 * SDK предоставляется компанией-производителем.
 */
@Singleton
class Neva01FFiscalCore @Inject constructor(
    private val context: Context
) : FiscalCore {

    private val TAG = "Neva01FFiscalCore"

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedStatus: FiscalStatus? = null

    private val sdk: Neva01FProtocol by lazy {
        RealNeva01FProtocol(context)
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Нева 01Ф...")
            // sdk.initialize(context)
            initialized = true
            Log.d(TAG, "Нева 01Ф initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Нева 01Ф", e)
            false
        }
    }

    override suspend fun shutdown(): Unit = withContext(Dispatchers.IO) {
        initialized = false
        // sdk.shutdown()
        Log.d(TAG, "Нева 01Ф shut down")
    }

    override suspend fun openShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: opening shift")
        sdk.openShift()
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing SALE check ${check.id}")
        sdk.printSale(check)
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing RETURN check ${check.id}")
        sdk.printReturn(check)
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing CORRECTION ${doc.id}")
        sdk.printCorrection(doc)
    }

    override suspend fun closeShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: closing shift")
        cachedStatus = null
        sdk.closeShift()
    }

    override suspend fun printXReport(): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: X-report")
        sdk.printXReport()
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: cash in ${amount.rubles}₽")
        sdk.cashIn(amount, comment)
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: cash out ${amount.rubles}₽")
        sdk.cashOut(amount, comment)
    }

    override suspend fun getStatus(): FiscalStatus = withContext(Dispatchers.IO) {
        cachedStatus ?: sdk.getStatus().also { cachedStatus = it }
    }

    override suspend fun getFFDVersion(): FFDVersion = withContext(Dispatchers.IO) {
        sdk.getFFDVersion()
    }

    private suspend fun <T> executeFiscal(block: suspend () -> T): T {
        check(initialized) { "SDK not initialized" }
        return try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: FiscalException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fiscal op failed", e)
            throw FiscalException(-1, e.message ?: "Unknown", recoverable = false)
        }
    }
}

interface Neva01FProtocol {
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

class RealNeva01FProtocol(private val context: Context) : Neva01FProtocol {
    // До поставки отдельного Neva SDK-контракта используем runtime binder bridge фискального сервиса.
    private val fallbackBridge by lazy { RealMSPOSKProtocol(context) }

    override suspend fun openShift(): FiscalResult = fallbackBridge.openShift()

    override suspend fun printSale(check: FiscalCheck): FiscalResult = fallbackBridge.printSale(check)

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = fallbackBridge.printReturn(check)

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = fallbackBridge.printCorrection(doc)

    override suspend fun closeShift(): FiscalResult = fallbackBridge.closeShift()

    override suspend fun printXReport(): FiscalResult = fallbackBridge.printXReport()

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = fallbackBridge.cashIn(amount, comment)

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = fallbackBridge.cashOut(amount, comment)

    override suspend fun getStatus(): FiscalStatus = fallbackBridge.getStatus()

    override suspend fun getFFDVersion(): FFDVersion = fallbackBridge.getFFDVersion()
}
