package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.TaxSystem
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalShift
import com.vitbon.kkm.features.auth.domain.CashierRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessSaleUseCaseTest {

    @Test
    fun `process sale returns access denied when role is missing`() = runTest {
        val orchestrator = mockk<FiscalOperationOrchestrator>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val shiftDao = mockk<ShiftDao>(relaxed = true)
        coEvery { shiftDao.findOpenShift() } returns null
        val fiscalConfig = mockk<FiscalConfig>(relaxed = true)
        val useCase = ProcessSaleUseCase(orchestrator, checkDao, checkItemDao, shiftDao, fiscalConfig)

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
            shiftId = "shift-1",
            cashierRole = null,
            emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        result as SaleResult.FiscalError
        assertEquals(-1, result.code)
        assertEquals("Операция запрещена для текущей роли", result.message)
        coVerify(exactly = 0) { orchestrator.executeSale(any()) }
        coVerify(exactly = 0) { checkDao.insert(any()) }
        coVerify(exactly = 0) { checkItemDao.insertAll(any()) }
    }

    @Test
    fun `process sale returns access denied during emergency session`() = runTest {
        val orchestrator = mockk<FiscalOperationOrchestrator>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val shiftDao = mockk<ShiftDao>(relaxed = true)
        coEvery { shiftDao.findOpenShift() } returns null
        val fiscalConfig = mockk<FiscalConfig>(relaxed = true)
        val useCase = ProcessSaleUseCase(orchestrator, checkDao, checkItemDao, shiftDao, fiscalConfig)

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
            shiftId = "shift-1",
            cashierRole = CashierRole.ADMIN,
            emergencySessionActive = true
        )

        assertTrue(result is SaleResult.FiscalError)
        result as SaleResult.FiscalError
        assertEquals(-1, result.code)
        assertEquals("Операция запрещена для текущей роли", result.message)
        coVerify(exactly = 0) { orchestrator.executeSale(any()) }
        coVerify(exactly = 0) { checkDao.insert(any()) }
        coVerify(exactly = 0) { checkItemDao.insertAll(any()) }
    }

    @Test
    fun `process sale delegates to orchestrator and returns success`() = runTest {
        val orchestrator = mockk<FiscalOperationOrchestrator>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val shiftDao = mockk<ShiftDao>(relaxed = true)
        coEvery { shiftDao.findOpenShift() } returns null

        coEvery { orchestrator.executeSale(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "fs",
            fnNumber = "fn",
            fdNumber = "fd",
            ffdVersion = "1.2"
        )

        val fiscalConfig = mockk<FiscalConfig>(relaxed = true)
        val useCase = ProcessSaleUseCase(orchestrator, checkDao, checkItemDao, shiftDao, fiscalConfig)
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
            cashierId = "cashier-28",
            deviceId = "device-1",
            shiftId = "shift-1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        assertTrue(result is SaleResult.Success)
        result as SaleResult.Success
        assertEquals("fs", result.fiscalSign)
        coVerify(exactly = 1) { orchestrator.executeSale(any()) }
        coVerify(exactly = 1) { checkDao.updateSyncStatus(any(), "PENDING_SYNC", "fs", null, null) }
    }

    @Test
    fun `process sale — FiscalCheck additionalInfo contains taxSystem shiftNumber receiptNumberInShift orgInn`() = runTest {
        val orchestrator = mockk<FiscalOperationOrchestrator>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val shiftDao = mockk<ShiftDao>(relaxed = true)
        val mockShift = LocalShift(
            id = "shift-42",
            cashierId = "cashier-1",
            deviceId = "device-1",
            openedAt = System.currentTimeMillis(),
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )
        coEvery { shiftDao.findOpenShift() } returns mockShift

        coEvery { orchestrator.executeStatusCheck() } returns FiscalStatus(
            fnRegistered = true,
            fnNumber = "fn",
            shiftOpen = true,
            shiftAgeHours = 2L,
            currentFdNumber = 7,
            ofdConnected = true,
            lastError = null
        )

        val fiscalConfig = FiscalConfig(
            taxSystem = TaxSystem.USN_INCOME,
            orgInn = "770123456789"
        )
        val useCase = ProcessSaleUseCase(orchestrator, checkDao, checkItemDao, shiftDao, fiscalConfig)
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Test",
                    quantity = 1.0, price = Money(1000), discount = Money.ZERO,
                    vatRate = VatRate.VAT_22
                )
            ),
            globalDiscount = Money.ZERO,
            paymentType = PaymentType.CARD
        )

        val checkSlot = slot<FiscalCheck>()
        coEvery { orchestrator.executeSale(capture(checkSlot)) } returns FiscalRuntimeResult.Success(
            fiscalSign = "fs",
            fnNumber = "fn",
            fdNumber = "8",
            ffdVersion = "1.2"
        )

        useCase.execute(
            cart = cart,
            cashierId = "cashier-1",
            deviceId = "device-1",
            shiftId = "shift-1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        val captured = checkSlot.captured
        assertEquals("2", captured.additionalInfo["taxSystem"])
        assertEquals("shift-42", captured.additionalInfo["shiftNumber"])
        assertEquals("8", captured.additionalInfo["receiptNumberInShift"])
        assertEquals("770123456789", captured.additionalInfo["orgInn"])
    }
}
