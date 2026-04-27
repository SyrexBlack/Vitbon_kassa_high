package com.vitbon.kkm.features.correction.domain

import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionUseCaseTest {

    private val orchestrator = mockk<FiscalOperationOrchestrator>()
    private val useCase = CorrectionUseCase(orchestrator)

    @Test
    fun `process delegates correction to orchestrator and maps success`() = runBlocking {
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
    fun `process maps orchestrator error`() = runBlocking {
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
}
