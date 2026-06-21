package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalCore

/**
 * Фабрика FiscalCore для DI.
 *
 * В debug-режиме возвращает [FakeFiscalCore] (in-memory implementation) для локальной разработки
 * без ККТ. В release использует реальный [FiscalCoreProvider] который выбирает MSPOS-K
 * или Нева 01Ф на основе настроек.
 *
 * @param isDebug build-type флаг (BuildConfig.DEBUG)
 * @param context Application context
 * @param realCoreProvider lazy provider для production FiscalCore (создаёт MSPOS-K/Нева)
 * @return готовый к использованию [FiscalCore]
 */
fun createFiscalCore(
    isDebug: Boolean,
    context: Context,
    realCoreProvider: () -> FiscalCore
): FiscalCore {
    return if (isDebug) {
        FakeFiscalCore()
    } else {
        realCoreProvider()
    }
}

/**
 * Fake FiscalCore для debug-сборок и unit/instrumented тестов.
 * Состояние смены в памяти, чеки возвращаются с мок fiscalSign.
 * Использует MSPOS-K protocol shell (без реального SDK), имитируя успешные операции.
 */
private class FakeFiscalCore : FiscalCore {
    private var shiftOpen: Boolean = false
    private var ffdVersion: String = "1.05"
    private val printedChecks = mutableListOf<Long>()
    private var nextFiscalSign: Long = 1000L

    override suspend fun openShift(): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-2, "Смена уже открыта")
            )
        }
        shiftOpen = true
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = System.currentTimeMillis(),
            fiscalSign = 0L,
            warnings = emptyList()
        )
    }

    override suspend fun printSale(
        check: com.vitbon.kkm.core.fiscal.model.FiscalCheck,
        cashierName: String,
        cashierInn: String?
    ): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        val sign = nextFiscalSign++
        printedChecks.add(sign)
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = sign,
            warnings = emptyList()
        )
    }

    override suspend fun printReturn(
        check: com.vitbon.kkm.core.fiscal.model.FiscalCheck,
        cashierName: String,
        cashierInn: String?
    ): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        val sign = nextFiscalSign++
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = sign,
            warnings = emptyList()
        )
    }

    override suspend fun printCorrection(
        doc: com.vitbon.kkm.core.fiscal.model.CorrectionDoc,
        cashierName: String,
        cashierInn: String?
    ): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        val sign = nextFiscalSign++
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = sign,
            warnings = emptyList()
        )
    }

    override suspend fun closeShift(): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        shiftOpen = false
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = nextFiscalSign++,
            warnings = emptyList()
        )
    }

    override suspend fun printXReport(): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = 0L,
            warnings = emptyList()
        )
    }

    override suspend fun cashIn(
        amount: com.vitbon.kkm.core.fiscal.model.Money,
        comment: String?
    ): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = nextFiscalSign++,
            warnings = emptyList()
        )
    }

    override suspend fun cashOut(
        amount: com.vitbon.kkm.core.fiscal.model.Money,
        comment: String?
    ): com.vitbon.kkm.core.fiscal.model.FiscalResult {
        if (!shiftOpen) {
            return com.vitbon.kkm.core.fiscal.model.FiscalResult.Error(
                com.vitbon.kkm.core.fiscal.FiscalException(-3, "Смена не открыта")
            )
        }
        return com.vitbon.kkm.core.fiscal.model.FiscalResult.Success(
            shiftId = 0L,
            fiscalSign = nextFiscalSign++,
            warnings = emptyList()
        )
    }

    override suspend fun getStatus(): com.vitbon.kkm.core.fiscal.model.FiscalStatus {
        return com.vitbon.kkm.core.fiscal.model.FiscalStatus(
            shiftOpen = shiftOpen,
            fiscalSign = if (shiftOpen) nextFiscalSign - 1 else 0L,
            paperLow = false,
            ffdVersion = ffdVersion
        )
    }

    override suspend fun getFFDVersion(): com.vitbon.kkm.core.fiscal.model.FFDVersion {
        return com.vitbon.kkm.core.fiscal.model.FFDVersion.fromString(ffdVersion)
    }

    override suspend fun initialize(): Boolean = true

    override suspend fun shutdown() {
        shiftOpen = false
        printedChecks.clear()
    }
}
