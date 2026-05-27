package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalCore

internal fun createFiscalCore(
    isDebug: Boolean,
    context: Context,
    realCoreProvider: () -> FiscalCore
): FiscalCore {
    return realCoreProvider()
}