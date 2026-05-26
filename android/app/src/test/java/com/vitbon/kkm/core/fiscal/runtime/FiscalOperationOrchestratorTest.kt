package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscalOperationOrchestratorTest {

    private val defaultCashierProvider = object : CashierNameProvider {
        override fun getCashierNameAndInn() = Pair("Кассир", null)
    }

    private fun mockRootRiskGuard(unblocked: Boolean = true): RootRiskGuard {
        val guard = mockk<RootRiskGuard>()
        every { guard.getCurrentBlockingState() } returns if (unblocked) {
            AppBlockingState.Unblocked
        } else {
            AppBlockingState.Blocked("Устройство скомпрометировано")
        }
        return guard
    }

    private fun mockFiscalStatus(shiftAgeHours: Long? = null): FiscalStatus = FiscalStatus(
        fnRegistered = true,
        fnNumber = "fn-1",
        shiftOpen = true,
        shiftAgeHours = shiftAgeHours,
        currentFdNumber = 100,
        ofdConnected = true,
        lastError = null
    )

    @Test
    fun `sale retries once on format error and then succeeds`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(false) } returns "1.05"
        coEvery { resolver.resolve(true) } returns "1.2"
        coEvery { core.getStatus() } returns mockFiscalStatus()

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())

        coEvery { core.printSale(check, any(), any()) } throws FiscalException(1001, "invalid format", true) andThen
            FiscalResult.Success("fs", "fn", "fd", 1L)

        val orchestrator = FiscalOperationOrchestrator(core, resolver, mockRootRiskGuard(), defaultCashierProvider)
        val result = orchestrator.executeSale(check)

        assertEquals(FiscalRuntimeResult.Success::class, result::class)
        coVerify(exactly = 2) { core.printSale(check, "Кассир", null) }
        coVerify { resolver.resolve(true) }
    }

    @Test
    fun `executeSale returns security blocked when root detected`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(any()) } returns "1.05"

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())
        val guard = mockRootRiskGuard(unblocked = false)

        val orchestrator = FiscalOperationOrchestrator(core, resolver, guard, defaultCashierProvider)
        val result = orchestrator.executeSale(check)

        assertTrue(result is FiscalRuntimeResult.Error)
        val error = result as FiscalRuntimeResult.Error
        assertEquals("SECURITY_BLOCKED", error.code)
        assertTrue(error.message.contains("скомпрометировано"))
        coVerify(exactly = 0) { core.printSale(any(), any(), any()) }
    }

    @Test
    fun `executeSale includes shift age warning when shift is older than 24 hours`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(false) } returns "1.05"
        coEvery { core.getStatus() } returns mockFiscalStatus(shiftAgeHours = 25)
        coEvery { core.printSale(any(), any(), any()) } returns FiscalResult.Success("fs", "fn", "fd", 1L)

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())
        val orchestrator = FiscalOperationOrchestrator(core, resolver, mockRootRiskGuard(), defaultCashierProvider)
        val result = orchestrator.executeSale(check)

        assertTrue(result is FiscalRuntimeResult.Success)
        val success = result as FiscalRuntimeResult.Success
        assertEquals(1, success.warnings.size)
        assertTrue(success.warnings[0].contains("25ч"))
        assertTrue(success.warnings[0].contains("Закройте смену"))
    }

    @Test
    fun `executeSale has no warnings when shift age is under 24 hours`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(false) } returns "1.05"
        coEvery { core.getStatus() } returns mockFiscalStatus(shiftAgeHours = 8)
        coEvery { core.printSale(any(), any(), any()) } returns FiscalResult.Success("fs", "fn", "fd", 1L)

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())
        val orchestrator = FiscalOperationOrchestrator(core, resolver, mockRootRiskGuard(), defaultCashierProvider)
        val result = orchestrator.executeSale(check)

        assertTrue(result is FiscalRuntimeResult.Success)
        val success = result as FiscalRuntimeResult.Success
        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun `executeSale ignores getStatus errors and proceeds without warnings`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(false) } returns "1.05"
        coEvery { core.getStatus() } throws RuntimeException("status unavailable")
        coEvery { core.printSale(any(), any(), any()) } returns FiscalResult.Success("fs", "fn", "fd", 1L)

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())
        val orchestrator = FiscalOperationOrchestrator(core, resolver, mockRootRiskGuard(), defaultCashierProvider)
        val result = orchestrator.executeSale(check)

        assertTrue(result is FiscalRuntimeResult.Success)
        val success = result as FiscalRuntimeResult.Success
        assertTrue(success.warnings.isEmpty())
    }
}