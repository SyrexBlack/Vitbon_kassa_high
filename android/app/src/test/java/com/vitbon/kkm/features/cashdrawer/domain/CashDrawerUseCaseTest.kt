package com.vitbon.kkm.features.cashdrawer.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashDrawerUseCaseTest {

    private val orchestrator = mockk<FiscalOperationOrchestrator>()
    private val useCase = CashDrawerUseCase(orchestrator)

    @Test
    fun `cashIn delegates to orchestrator and maps success`() = runBlocking {
        coEvery { orchestrator.executeCashIn(any(), any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "FS-IN-1",
            fnNumber = "FN-1",
            fdNumber = "FD-1",
            ffdVersion = "1.2"
        )

        val result = useCase.cashIn(Money(1000), "note")

        assertTrue(result is CashDrawerResult.Success)
        assertEquals("FS-IN-1", (result as CashDrawerResult.Success).fiscalSign)
        coVerify(exactly = 1) { orchestrator.executeCashIn(any(), any()) }
    }

    @Test
    fun `cashOut delegates to orchestrator and maps error`() = runBlocking {
        coEvery { orchestrator.executeCashOut(any(), any()) } returns FiscalRuntimeResult.Error(
            code = "FISCAL_ERROR",
            message = "cash out failed",
            recoverable = false
        )

        val result = useCase.cashOut(Money(1000), "note")

        assertTrue(result is CashDrawerResult.Error)
        result as CashDrawerResult.Error
        assertEquals(-1, result.code)
        assertEquals("cash out failed", result.message)
        coVerify(exactly = 1) { orchestrator.executeCashOut(any(), any()) }
    }
}
