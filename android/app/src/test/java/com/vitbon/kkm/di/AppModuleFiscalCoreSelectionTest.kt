package com.vitbon.kkm.di

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.runtime.CashierNameProvider
import com.vitbon.kkm.core.fiscal.runtime.FfdPolicyStore
import com.vitbon.kkm.core.fiscal.runtime.FfdVersionResolver
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        assertNotNull(core)
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

    @Test
    fun `app module provides fiscal runtime components`() {
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        val core = mockk<FiscalCore>()

        val store = AppModule.provideFfdPolicyStore(prefs)
        val resolver = AppModule.provideFfdVersionResolver(core, store)
        val rootRiskGuard = mockk<RootRiskGuard>()
        every { rootRiskGuard.getCurrentBlockingState() } returns AppBlockingState.Unblocked
        val cashierProvider = mockk<CashierNameProvider>()
        every { cashierProvider.getCashierNameAndInn() } returns Pair("Кассир", null)
        val orchestrator = AppModule.provideFiscalOperationOrchestrator(
            core,
            resolver,
            store,
            rootRiskGuard,
            cashierProvider
        )

        assertNotNull(store)
        assertNotNull(resolver)
        assertNotNull(orchestrator)
        assertTrue(store is FfdPolicyStore)
        assertTrue(resolver is FfdVersionResolver)
        assertTrue(orchestrator is FiscalOperationOrchestrator)
    }
}
