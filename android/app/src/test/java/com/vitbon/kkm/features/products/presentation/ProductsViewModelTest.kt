package com.vitbon.kkm.features.products.presentation

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.sync.ProductSyncResult
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.features.products.domain.Product
import com.vitbon.kkm.features.products.domain.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ProductRepository>()
    private val syncService = mockk<SyncService>()

    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeAll() } returns productsFlow
        coEvery { repository.search(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh toggles loading and updates products on sync success`() = runTest {
        val syncedProducts = listOf(
            Product(
                id = "p1",
                barcode = "4607001234567",
                name = "Вода 0.5л",
                article = "WATER-05",
                price = Money(12900),
                vatRate = VatRate.NO_VAT,
                stock = 7.0,
                egaisFlag = false,
                chaseznakFlag = false
            )
        )

        coEvery { syncService.syncProductsNow() } coAnswers {
            productsFlow.value = syncedProducts
            ProductSyncResult(received = 1, deleted = 0)
        }

        val vm = ProductsViewModel(repository, syncService)

        val refreshJob = launch {
            vm.refresh()
        }
        advanceUntilIdle()
        refreshJob.cancel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.products.size)
        assertEquals("Вода 0.5л", state.products.first().name)
        assertNull(state.error)
        coVerify(exactly = 1) { syncService.syncProductsNow() }
    }

    @Test
    fun `refresh sets error when sync throws and keeps loading false`() = runTest {
        coEvery { syncService.syncProductsNow() } throws RuntimeException("timeout")

        val vm = ProductsViewModel(repository, syncService)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("timeout", state.error)
        coVerify(exactly = 1) { syncService.syncProductsNow() }
    }
}
