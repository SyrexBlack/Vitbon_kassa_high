package com.vitbon.kkm.features.sales.presentation


import com.vitbon.kkm.core.sync.SyncPrefs
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalShift
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakResult
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakValidation
import com.vitbon.kkm.features.sales.domain.CartItem
import com.vitbon.kkm.features.sales.domain.MarkedGoodsSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import com.vitbon.kkm.features.sales.domain.ScanBarcodeUseCase
import com.vitbon.kkm.features.sales.domain.ScanResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scanBarcode = mockk<ScanBarcodeUseCase>()
    private val processSale = mockk<MarkedGoodsSaleUseCase>()
    private val authUseCase = mockk<AuthUseCase>()
    private val syncService = mockk<SyncService>(relaxed = true)
    private val shiftDao = mockk<ShiftDao>()
    private val syncPrefs = mockk<SyncPrefs>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authUseCase.isEmergencySessionActive() } returns false
        every { authUseCase.auditEmergencyOperationDenied(any()) } returns Unit
        every { syncPrefs.deviceId } returns "secure-device-1"
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
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER
        coEvery { shiftDao.findOpenShift() } returns openShift
        coEvery { processSale.execute(any(), any(), any(), any(), any(), any()) } returns SaleResult.Success(
            checkId = "check-1",
            fiscalSign = "fs-1",
            total = 129.0
        )

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao, syncPrefs)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.setPayment(PaymentType.CASH)
        vm.processSale()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            processSale.execute(any(), "cashier-1", "secure-device-1", "shift-open-1", CashierRole.CASHIER, false)
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
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { processSale.execute(any(), any(), any(), any(), any(), any()) } returns SaleResult.Success(
            checkId = "check-2",
            fiscalSign = "fs-2",
            total = 129.0
        )

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao, syncPrefs)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.setPayment(PaymentType.CASH)
        vm.processSale()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            processSale.execute(any(), "cashier-1", "secure-device-1", null, CashierRole.CASHIER, false)
        }
    }

    @Test
    fun `processSale denies when role is missing`() = runTest {
        val item = CartItem(
            productId = "p1",
            barcode = "4607001234567",
            name = "Вода",
            quantity = 1.0,
            price = Money(12_900L),
            vatRate = VatRate.NO_VAT
        )

        coEvery { scanBarcode.execute("4607001234567") } returns ScanResult.Found(item)
        coEvery { shiftDao.findOpenShift() } returns null
        every { authUseCase.getCurrentCashierId() } returns "unknown"
        every { authUseCase.getCurrentCashierRole() } returns null
        coEvery {
            processSale.execute(any(), any(), any(), any(), any(), any())
        } returns SaleResult.FiscalError(-1, "Операция запрещена для текущей роли")

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao, syncPrefs)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.processSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isProcessing)
        assertEquals(true, state.saleResult is SaleResult.FiscalError)
        assertEquals("Операция запрещена для текущей роли", (state.saleResult as SaleResult.FiscalError).message)
        coVerify(exactly = 1) {
            processSale.execute(any(), "unknown", "secure-device-1", null, null, false)
        }
    }

    @Test
    fun `processSale denies during active emergency session`() = runTest {
        val item = CartItem(
            productId = "p1",
            barcode = "4607001234567",
            name = "Вода",
            quantity = 1.0,
            price = Money(12_900L),
            vatRate = VatRate.NO_VAT
        )

        coEvery { scanBarcode.execute("4607001234567") } returns ScanResult.Found(item)
        coEvery { shiftDao.findOpenShift() } returns null
        every { authUseCase.getCurrentCashierId() } returns "unknown"
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        every { authUseCase.isEmergencySessionActive() } returns true
        coEvery {
            processSale.execute(any(), any(), any(), any(), any(), any())
        } returns SaleResult.FiscalError(-1, "Операция запрещена для текущей роли")

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao, syncPrefs)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.processSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isProcessing)
        assertEquals(true, state.saleResult is SaleResult.FiscalError)
        assertEquals("Операция запрещена для текущей роли", (state.saleResult as SaleResult.FiscalError).message)
        verify(exactly = 1) { authUseCase.auditEmergencyOperationDenied("SALE") }
        coVerify(exactly = 1) {
            processSale.execute(any(), "unknown", "secure-device-1", null, CashierRole.ADMIN, true)
        }
    }

    @Test
    fun `processSale blocks when marked code is already sold`() = runTest {
        val item = CartItem(
            productId = "p1",
            barcode = "4607001234567",
            name = "Сигареты",
            quantity = 1.0,
            price = Money(20_000L),
            vatRate = VatRate.VAT_22,
            markedProductCode = "01046123456789052FnS+EqV1XNAmLqT"
        )

        coEvery { scanBarcode.execute("4607001234567") } returns ScanResult.Found(item)
        every { authUseCase.getCurrentCashierId() } returns "cashier-1"
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { processSale.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.FiscalError(-1, "CHASENAK_BLOCK: ALREADY_SOLD — код невозможно продать")

        val vm = SalesViewModel(scanBarcode, processSale, authUseCase, syncService, shiftDao, syncPrefs)

        vm.search("4607001234567")
        advanceUntilIdle()
        vm.processSale()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(false, state.isProcessing)
        assertEquals(true, state.saleResult is SaleResult.FiscalError)
        assertEquals(true, (state.saleResult as SaleResult.FiscalError).message.startsWith("CHASENAK_BLOCK"))
    }
}
