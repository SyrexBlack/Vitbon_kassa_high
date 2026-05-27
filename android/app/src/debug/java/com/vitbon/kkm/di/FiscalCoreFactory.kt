package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalCore
import kotlinx.coroutines.runBlocking

internal fun createFiscalCore(
    isDebug: Boolean,
    context: Context,
    realCoreProvider: () -> FiscalCore
): FiscalCore {
    if (isDebug) {
        return runBlocking {
            FakeFiscalCore(context).also { it.initialize() }
        }
    }
    return realCoreProvider()
}