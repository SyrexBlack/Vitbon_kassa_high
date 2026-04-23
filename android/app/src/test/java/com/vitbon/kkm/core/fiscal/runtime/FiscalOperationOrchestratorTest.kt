package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FiscalOperationOrchestratorTest {

    @Test
    fun `sale retries once on format error and then succeeds`() = runTest {
        val core = mockk<FiscalCore>()
        val resolver = mockk<FfdVersionResolver>()
        coEvery { resolver.resolve(false) } returns "1.05"
        coEvery { resolver.resolve(true) } returns "1.2"

        val check = FiscalCheck("1", CheckType.SALE, emptyList(), emptyList())

        coEvery { core.printSale(check) } throws FiscalException(1001, "invalid format", true) andThen
            FiscalResult.Success("fs", "fn", "fd", 1L)

        val orchestrator = FiscalOperationOrchestrator(core, resolver)
        val result = orchestrator.executeSale(check)

        assertEquals(FiscalRuntimeResult.Success::class, result::class)
        coVerify(exactly = 2) { core.printSale(check) }
        coVerify { resolver.resolve(true) }
    }
}
