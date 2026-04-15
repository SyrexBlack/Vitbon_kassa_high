package com.vitbon.kkm.features.returns.presentation

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.returns.domain.ReturnItem
import com.vitbon.kkm.features.returns.domain.ReturnResult
import com.vitbon.kkm.features.returns.domain.ReturnUseCase
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val returnUseCase = mockk<ReturnUseCase>()
    private val authUseCase = mockk<AuthUseCase>()
    private val syncService = mockk<SyncService>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onCheckInput loads real items and recalculates return total`() = runTest {
        val sourceCheck = localSaleCheck(id = "sale-0001")
        coEvery { returnUseCase.findCheckByNumber("sale-0001") } returns sourceCheck
        coEvery { returnUseCase.loadCheckItems("sale-0001") } returns listOf(
            ReturnItem(
                productId = "prod-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 2.0,
                price = Money(100_00L),
                discount = Money(10_00L),
                vatRate = VatRate.VAT_10
            ),
            ReturnItem(
                productId = "prod-2",
                barcode = "4607009990000",
                name = "Сок",
                quantity = 1.0,
                price = Money(50_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )

        val vm = ReturnViewModel(returnUseCase, authUseCase, syncService)
        vm.onCheckInput("sale-0001")
        advanceUntilIdle()

        val st = vm.state.value
        assertEquals(sourceCheck, st.originalCheck)
        assertEquals(2, st.returnItems.size)
        assertEquals(240_00L, st.returnTotal.kopecks)
        assertEquals("Вода", st.returnItems[0].name)
        assertEquals("Сок", st.returnItems[1].name)
    }

    @Test
    fun `onCheckInput with short identifier still loads source check and items`() = runTest {
        val sourceCheck = localSaleCheck(id = "sale-1")
        coEvery { returnUseCase.findCheckByNumber("sale-1") } returns sourceCheck
        coEvery { returnUseCase.loadCheckItems("sale-1") } returns listOf(
            ReturnItem(
                productId = "prod-short",
                barcode = "4607000000001",
                name = "Кофе",
                quantity = 1.0,
                price = Money(70_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )

        val vm = ReturnViewModel(returnUseCase, authUseCase, syncService)
        vm.onCheckInput("sale-1")
        advanceUntilIdle()

        val st = vm.state.value
        assertEquals(sourceCheck, st.originalCheck)
        assertEquals(1, st.returnItems.size)
        assertEquals("Кофе", st.returnItems.first().name)
    }

    @Test
    fun `onCheckInput ignores stale async result when input changed and clears loaded source`() = runTest {
        val sourceCheck = localSaleCheck(id = "sale-0003")
        coEvery { returnUseCase.findCheckByNumber("sale-0003") } returns sourceCheck
        coEvery { returnUseCase.loadCheckItems("sale-0003") } returns listOf(
            ReturnItem(
                productId = "prod-stale",
                barcode = "4607000000002",
                name = "Чай",
                quantity = 1.0,
                price = Money(80_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )
        coEvery { returnUseCase.findCheckByNumber("x") } returns null

        val vm = ReturnViewModel(returnUseCase, authUseCase, syncService)
        vm.onCheckInput("sale-0003")
        vm.onCheckInput("x")
        advanceUntilIdle()

        val st = vm.state.value
        assertEquals("x", st.checkInput)
        assertTrue(st.originalCheck == null)
        assertTrue(st.returnItems.isEmpty())
        assertEquals(0L, st.returnTotal.kopecks)
        assertTrue(st.error == null)
    }

    @Test
    fun `onCheckInput drops stale item load result when input changes after lookup`() = runTest {
        lateinit var vm: ReturnViewModel

        val sourceCheck = localSaleCheck(id = "sale-0004")
        coEvery { returnUseCase.findCheckByNumber("sale-0004") } returns sourceCheck
        coEvery { returnUseCase.loadCheckItems("sale-0004") } coAnswers {
            vm.onCheckInput("x")
            listOf(
                ReturnItem(
                    productId = "prod-race",
                    barcode = "4607000000004",
                    name = "Сыр",
                    quantity = 1.0,
                    price = Money(90_00L),
                    discount = Money.ZERO,
                    vatRate = VatRate.NO_VAT
                )
            )
        }
        coEvery { returnUseCase.findCheckByNumber("x") } returns null

        vm = ReturnViewModel(returnUseCase, authUseCase, syncService)
        vm.onCheckInput("sale-0004")
        advanceUntilIdle()

        val st = vm.state.value
        assertEquals("x", st.checkInput)
        assertTrue(st.originalCheck == null)
        assertTrue(st.returnItems.isEmpty())
        assertEquals(0L, st.returnTotal.kopecks)
        assertTrue(st.error == null)
    }

    @Test
    fun `processReturn without source check does not stay in processing and sets user error`() = runTest {
        val vm = ReturnViewModel(returnUseCase, authUseCase, syncService)

        vm.processReturn()
        advanceUntilIdle()

        val st = vm.state.value
        assertFalse(st.isProcessing)
        assertEquals("Сначала выберите исходный чек продажи", st.error)
        coVerify(exactly = 0) { returnUseCase.processReturn(any(), any(), any()) }
        verify(exactly = 0) { syncService.onCheckCreated() }
    }

    @Test
    fun `processReturn success triggers sync once and passes loaded discount`() = runTest {
        val sourceCheck = localSaleCheck(id = "sale-0002")
        coEvery { returnUseCase.findCheckByNumber("sale-0002") } returns sourceCheck
        coEvery { returnUseCase.loadCheckItems("sale-0002") } returns listOf(
            ReturnItem(
                productId = "prod-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 1.0,
                price = Money(129_00L),
                discount = Money(9_00L),
                vatRate = VatRate.NO_VAT
            )
        )
        every { authUseCase.getCurrentCashierId() } returns "cashier-1"
        coEvery {
            returnUseCase.processReturn(
                sourceCheck,
                match { items ->
                    items.size == 1 &&
                        items[0].name == "Вода" &&
                        items[0].price == Money(129_00L) &&
                        items[0].discount == Money(9_00L)
                },
                "cashier-1"
            )
        } returns ReturnResult.Success(checkId = "return-1", fiscalSign = "FS-RET-1")

        val vm = ReturnViewModel(returnUseCase, authUseCase, syncService)
        vm.onCheckInput("sale-0002")
        advanceUntilIdle()

        vm.processReturn()
        advanceUntilIdle()

        val st = vm.state.value
        assertFalse(st.isProcessing)
        assertTrue(st.error == null)
        verify(exactly = 1) { syncService.onCheckCreated() }
    }

    private fun localSaleCheck(id: String): LocalCheck = LocalCheck(
        id = id,
        localUuid = "local-$id",
        shiftId = "shift-1",
        cashierId = "cashier-1",
        deviceId = "device-1",
        type = "sale",
        fiscalSign = "FS-SRC",
        ofdResponse = null,
        ffdVersion = "1.2",
        status = "SYNCED",
        subtotal = 100_00L,
        discount = 0L,
        total = 100_00L,
        taxAmount = 0L,
        paymentType = "cash",
        createdAt = 1_700_000_000_000L,
        syncedAt = 1_700_000_100_000L
    )
}
