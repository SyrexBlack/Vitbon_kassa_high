package com.vitbon.kkm.core.sync

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.*

/**
 * Fake FiscalCore для интеграционных UI-тестов.
 * Симулирует ККТ без реального оборудования.
 */
class FakeFiscalCore : FiscalCore {
    private var shiftOpen = false
    private var ffdVersion = FFDVersion.V1_05

    override suspend fun initialize() = true
    override suspend fun shutdown() {}

    override suspend fun openShift() = FiscalResult.Success(
        fiscalSign = "FAKE_SHIFT_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "1",
        timestamp = System.currentTimeMillis()
    ).also { shiftOpen = true }

    override suspend fun printSale(check: FiscalCheck) = FiscalResult.Success(
        fiscalSign = "FAKE_SALE_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = (check.id.hashCode() and 0xFFFF).toString(),
        timestamp = System.currentTimeMillis()
    )

    override suspend fun printReturn(check: FiscalCheck) = FiscalResult.Success(
        fiscalSign = "FAKE_RET_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    )

    override suspend fun printCorrection(doc: CorrectionDoc) = FiscalResult.Success(
        fiscalSign = "FAKE_CORR_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    )

    override suspend fun closeShift() = FiscalResult.Success(
        fiscalSign = "FAKE_CLOSE_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    ).also { shiftOpen = false }

    override suspend fun printXReport() = FiscalResult.Success(
        fiscalSign = "FAKE_XREP_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    )

    override suspend fun cashIn(amount: Money, comment: String?) = FiscalResult.Success(
        fiscalSign = "FAKE_CASHIN_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    )

    override suspend fun cashOut(amount: Money, comment: String?) = FiscalResult.Success(
        fiscalSign = "FAKE_CASHOUT_${System.currentTimeMillis()}",
        fnNumber = "0000000000FAKE1",
        fdNumber = "0",
        timestamp = System.currentTimeMillis()
    )

    override suspend fun getStatus() = FiscalStatus(
        fnRegistered = true,
        fnNumber = "0000000000FAKE1",
        shiftOpen = shiftOpen,
        shiftAgeHours = 1L,
        currentFdNumber = 42,
        ofdConnected = true,
        lastError = null
    )

    override suspend fun getFFDVersion() = ffdVersion

    fun setFFDVersion(v: FFDVersion) { ffdVersion = v }
    fun setShiftOpen(open: Boolean) { shiftOpen = open }
}
