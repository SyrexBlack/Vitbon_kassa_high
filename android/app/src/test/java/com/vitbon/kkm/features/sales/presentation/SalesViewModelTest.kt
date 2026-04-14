package com.vitbon.kkm.features.sales.presentation

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalShift
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.sales.domain.CartItem
import com.vitbon.kkm.features.sales.domain.ProcessSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import com.vitbon.kkm.features.sales.domain.ScanBarcodeUseCase
import com.vitbon.kkm.features.sales.domain.ScanResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanBarcode = mockk<ScanBarcodeUseCase>()
    private val processSale = mockk<ProcessSaleUseCase>()
    private val authUseCase = mockk<AuthUseCase>()
    private val syncService = mockk<SyncService>(relaxed = true)
    private val shiftDao = mockk<ShiftDao>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `processSale uses currently open shift id`() = runTest {
        val item = CartItem(
            productId = "p1",
            barcode = "4607001234567",
            name = "Вода",
            quantity = 1.0,
            price = Money(12_900L),
            vatRate = VatRate.NO_VAT
        )
        val openShift = LocalShift(
            id = "shift-open-1",
            cashierId = "cashier-1",
            deviceId = "device-1",
            openedAt = 1L,
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )

        coEvery { scanBarcode.execute("4607001234567") } returns ScanResult.Found(item)
        every { authUseCase.getCurrentCashierId() } returns "cashier-1"
        coEvery { shiftDao.findOpenShift() } returns openShift
        coEvery { processSale.execute(any(), any(), any(), any()) } returns SaleResult.Success(
            checkId = "check-1",
            fiscalSign = "fs-1",
            total = 129.0
        )

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.setPayment(PaymentType.CASH)
        vm.processSale()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            processSale.execute(any(), "cashier-1", any(), "shift-open-1")
        }
    }

    @Test
    fun `processSale passes null shift id when no open shift exists`() = runTest {
        val item = CartItem(
            productId = "p1",
            barcode = "4607001234567",
            name = "Вода",
            quantity = 1.0,
            price = Money(12_900L),
            vatRate = VatRate.NO_VAT
        )

        coEvery { scanBarcode.execute("4607001234567") } returns ScanResult.Found(item)
        every { authUseCase.getCurrentCashierId() } returns "cashier-1"
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { processSale.execute(any(), any(), any(), any()) } returns SaleResult.Success(
            checkId = "check-2",
            fiscalSign = "fs-2",
            total = 129.0
        )

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.setPayment(PaymentType.CASH)
        vm.processSale()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            processSale.execute(any(), "cashier-1", any(), null)
        }
    }
}
