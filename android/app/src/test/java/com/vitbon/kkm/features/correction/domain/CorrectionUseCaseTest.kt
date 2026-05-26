package com.vitbon.kkm.features.correction.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.CorrectionDoc
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.TaxSystem
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.auth.domain.RolePolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionUseCaseTest {

    private val orchestrator = mockk<FiscalOperationOrchestrator>()
    private val authUseCase = mockk<AuthUseCase>()
    private val fiscalConfig = FiscalConfig(taxSystem = TaxSystem.OSN, orgInn = null)
    private val useCase = CorrectionUseCase(orchestrator, authUseCase, fiscalConfig)

    @Test
    fun `process delegates correction to orchestrator and maps success`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        coEvery { orchestrator.executeCorrection(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "FS-CORR-1",
            fnNumber = "FN-1",
            fdNumber = "FD-1",
            ffdVersion = "1.2"
        )

        val result = useCase.process(
            type = CheckType.CORRECTION_INCOME,
            reason = "test",
            correctionNumber = "123",
            cashAmount = Money(1000),
            cardAmount = Money(0),
            vatRate = VatRate.VAT_22,
            cashierId = "cashier"
        )

        assertTrue(result is CorrectionResult.Success)
        assertEquals("FS-CORR-1", (result as CorrectionResult.Success).fiscalSign)
        coVerify(exactly = 1) { orchestrator.executeCorrection(any()) }
    }

    @Test
    fun `process maps orchestrator error`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        coEvery { orchestrator.executeCorrection(any()) } returns FiscalRuntimeResult.Error(
            code = "FISCAL_ERROR",
            message = "corr failed",
            recoverable = false
        )

        val result = useCase.process(
            type = CheckType.CORRECTION_EXPENSE,
            reason = "test",
            correctionNumber = "123",
            cashAmount = Money(1000),
            cardAmount = Money(0),
            vatRate = VatRate.VAT_22,
            cashierId = "cashier"
        )

        assertTrue(result is CorrectionResult.Error)
        result as CorrectionResult.Error
        assertEquals(-1, result.code)
        assertEquals("corr failed", result.message)
        coVerify(exactly = 1) { orchestrator.executeCorrection(any()) }
    }

    // ── Role enforcement ───────────────────────────────────────────────────

    @Test
    fun `process denies CASHIER role`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER

        val result = useCase.process(
            type = CheckType.CORRECTION_INCOME,
            reason = "test",
            correctionNumber = "1",
            cashAmount = Money(1000),
            cardAmount = Money.ZERO,
            vatRate = VatRate.VAT_22,
            cashierId = "cashier-1"
        )

        assertTrue(result is CorrectionResult.Error)
        assertEquals(-1, (result as CorrectionResult.Error).code)
        assertEquals(RolePolicy.ACCESS_DENIED_MESSAGE, result.message)
        coVerify(exactly = 0) { orchestrator.executeCorrection(any()) }
    }

    @Test
    fun `process denies SENIOR_CASHIER role`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.SENIOR_CASHIER

        val result = useCase.process(
            type = CheckType.CORRECTION_INCOME,
            reason = "test",
            correctionNumber = "2",
            cashAmount = Money(1000),
            cardAmount = Money.ZERO,
            vatRate = VatRate.VAT_22,
            cashierId = "senior-1"
        )

        assertTrue(result is CorrectionResult.Error)
        assertEquals(-1, (result as CorrectionResult.Error).code)
        assertEquals(RolePolicy.ACCESS_DENIED_MESSAGE, result.message)
        coVerify(exactly = 0) { orchestrator.executeCorrection(any()) }
    }

    @Test
    fun `process denies null role`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns null

        val result = useCase.process(
            type = CheckType.CORRECTION_EXPENSE,
            reason = "test",
            correctionNumber = "3",
            cashAmount = Money.ZERO,
            cardAmount = Money(500),
            vatRate = VatRate.VAT_10,
            cashierId = "cashier-2"
        )

        assertTrue(result is CorrectionResult.Error)
        assertEquals(-1, (result as CorrectionResult.Error).code)
        coVerify(exactly = 0) { orchestrator.executeCorrection(any()) }
    }

    @Test
    fun `process allows ADMIN role`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        coEvery { orchestrator.executeCorrection(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "FP-ADM",
            fnNumber = "FN-ADM",
            fdNumber = "FD-ADM",
            ffdVersion = "1.2"
        )

        val result = useCase.process(
            type = CheckType.CORRECTION_INCOME,
            reason = "test",
            correctionNumber = "4",
            cashAmount = Money(2000),
            cardAmount = Money.ZERO,
            vatRate = VatRate.VAT_22,
            cashierId = "admin-1"
        )

        assertTrue(result is CorrectionResult.Success)
        coVerify(exactly = 1) { orchestrator.executeCorrection(any()) }
    }

    @Test
    fun `process — CorrectionDoc receives taxSystem from FiscalConfig`() = runTest {
        coEvery { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        coEvery { orchestrator.executeCorrection(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "FS-TAX", fnNumber = "FN", fdNumber = "FD", ffdVersion = "1.2"
        )

        val cfg = FiscalConfig(taxSystem = TaxSystem.USN_INCOME_OUTCOME, orgInn = "770000000000")
        val uc = CorrectionUseCase(orchestrator, authUseCase, cfg)

        uc.process(
            type = CheckType.CORRECTION_INCOME,
            reason = "test",
            correctionNumber = "99",
            cashAmount = Money(1000),
            cardAmount = Money.ZERO,
            vatRate = VatRate.VAT_22,
            cashierId = "admin"
        )

        val captured = slot<CorrectionDoc>()
        coVerify { orchestrator.executeCorrection(capture(captured)) }
        assertEquals(TaxSystem.USN_INCOME_OUTCOME, captured.captured.taxSystem)
    }
}
