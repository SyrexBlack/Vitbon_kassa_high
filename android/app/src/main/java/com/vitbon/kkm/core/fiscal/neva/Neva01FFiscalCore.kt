package com.vitbon.kkm.core.fiscal.neva

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
        // Реальная реализация: Neva01FSDK.getInstance(context)
        // Заглушка — заменить на Neva01F.aar после получения
        Neva01FStub()
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
        FiscalResult.Success(
            fiscalSign = "NEVA_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = "1",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing SALE check ${check.id}")
        FiscalResult.Success(
            fiscalSign = "NEVA_FISCAL_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = (check.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printReturn(check: FiscalCheck): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing RETURN check ${check.id}")
        FiscalResult.Success(
            fiscalSign = "NEVA_RETURN_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = (check.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: printing CORRECTION ${doc.id}")
        FiscalResult.Success(
            fiscalSign = "NEVA_CORR_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = (doc.id.hashCode() and 0xFFFF).toString(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun closeShift(): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: closing shift")
        cachedStatus = null
        FiscalResult.Success(
            fiscalSign = "NEVA_CLOSE_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun printXReport(): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: X-report")
        FiscalResult.Success(
            fiscalSign = "NEVA_XREP_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: cash in ${amount.rubles}₽")
        FiscalResult.Success(
            fiscalSign = "NEVA_CASHIN_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = executeFiscal {
        Log.d(TAG, "Нева: cash out ${amount.rubles}₽")
        FiscalResult.Success(
            fiscalSign = "NEVA_CASHOUT_${System.currentTimeMillis()}",
            fnNumber = "0000000000012345",
            fdNumber = "0",
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun getStatus(): FiscalStatus = withContext(Dispatchers.IO) {
        cachedStatus ?: FiscalStatus(
            fnRegistered = true,
            fnNumber = "0000000000012345",
            shiftOpen = true,
            shiftAgeHours = 1L,
            currentFdNumber = 100,
            ofdConnected = true,
            lastError = null
        ).also { cachedStatus = it }
    }

    override suspend fun getFFDVersion(): FFDVersion = withContext(Dispatchers.IO) {
        try {
            // val v = sdk.getFFDVersion()
            // FFDVersion.fromString(v)
            FFDVersion.V1_05
        } catch (e: Exception) {
            FFDVersion.V1_05
        }
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
    fun initialize(context: Context)
    fun shutdown()
}

class Neva01FStub : Neva01FProtocol {
    override fun initialize(context: Context) {}
    override fun shutdown() {}
}
