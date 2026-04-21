package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalCore
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModuleFiscalCoreSelectionTest {

    @Test
    fun `debug build uses fake fiscal core and does not call real provider`() {
        val context = mockk<Context>(relaxed = true)
        var providerCalled = false

        val core = createFiscalCore(
            isDebug = true,
            context = context,
            realCoreProvider = {
                providerCalled = true
                mockk<FiscalCore>()
            }
        )

        assertTrue(core is FakeFiscalCore)
        assertFalse(providerCalled)
    }

    @Test
    fun `release build uses real fiscal core provider`() {
        val context = mockk<Context>(relaxed = true)
        var providerCalled = false
        val realCore = mockk<FiscalCore>()

        val core = createFiscalCore(
            isDebug = false,
            context = context,
            realCoreProvider = {
                providerCalled = true
                realCore
            }
        )

        assertSame(realCore, core)
        assertTrue(providerCalled)
    }
}
