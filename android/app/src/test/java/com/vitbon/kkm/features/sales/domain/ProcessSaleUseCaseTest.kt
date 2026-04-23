package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessSaleUseCaseTest {

    @Test
    fun `process sale delegates to orchestrator and returns success`() = runTest {
        val orchestrator = mockk<FiscalOperationOrchestrator>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)

        coEvery { orchestrator.executeSale(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "fs",
            fnNumber = "fn",
            fdNumber = "fd",
            ffdVersion = "1.2"
        )

        val useCase = ProcessSaleUseCase(orchestrator, checkDao, checkItemDao)
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1",
                    barcode = "4600000000000",
                    name = "Test item",
                    quantity = 1.0,
                    price = Money(1000),
                    discount = Money.ZERO,
                    vatRate = VatRate.VAT_22
                )
            ),
            globalDiscount = Money.ZERO,
            paymentType = PaymentType.CARD
        )

        val result = useCase.execute(
            cart = cart,
            cashierId = "cashier-1",
            deviceId = "device-1",
            shiftId = "shift-1"
        )

        assertTrue(result is SaleResult.Success)
        result as SaleResult.Success
        assertEquals("fs", result.fiscalSign)
        coVerify(exactly = 1) { orchestrator.executeSale(any()) }
        coVerify(exactly = 1) { checkDao.updateSyncStatus(any(), "PENDING_SYNC", "fs", null, null) }
    }
}
