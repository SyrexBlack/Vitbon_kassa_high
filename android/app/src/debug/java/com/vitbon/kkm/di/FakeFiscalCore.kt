package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.CorrectionDoc
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money

class FakeFiscalCore(private val context: Context) : FiscalCore {
    private var shiftOpen = false

    override suspend fun initialize(): Boolean = true
    override suspend fun shutdown(): Unit {}

    private fun successResult(): FiscalResult.Success {
        val ts = System.currentTimeMillis()
        return FiscalResult.Success(
            fiscalSign = "FAKE-$ts",
            fnNumber = "FAKE-FN-000000",
            fdNumber = (ts % 1000000).toString(),
            timestamp = ts
        )
    }

    override suspend fun openShift(): FiscalResult {
        shiftOpen = true
        return successResult()
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = successResult()
    override suspend fun printReturn(check: FiscalCheck): FiscalResult = successResult()
    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = successResult()

    override suspend fun closeShift(): FiscalResult {
        shiftOpen = false
        return successResult()
    }

    override suspend fun printXReport(): FiscalResult = successResult()
    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = successResult()
    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = successResult()

    override suspend fun getStatus(): FiscalStatus {
        return FiscalStatus(
            fnRegistered = true,
            fnNumber = "FAKE-FN-000000",
            shiftOpen = shiftOpen,
            shiftAgeHours = if (shiftOpen) 0L else null,
            currentFdNumber = 1,
            ofdConnected = true,
            lastError = null
        )
    }

    override suspend fun getFFDVersion(): FFDVersion = FFDVersion.V1_05
}
