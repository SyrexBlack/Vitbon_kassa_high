package com.vitbon.kkm.features.acceptance.presentation

import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.features.products.domain.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val productRepo = mockk<ProductRepository>()
    private val api = mockk<VitbonApi>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add item assigns deterministic ids and generated barcodes`() = runTest {
        val vm = AcceptanceViewModel(productRepo, api)

        vm.addItem(name = "A")
        vm.addItem(name = "B")

        val state = vm.state.value
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals("TEST-1", state.items[0].barcode)
        assertEquals("TEST-2", state.items[1].barcode)
    }

    @Test
    fun `update and remove affect only targeted item id even with same barcode`() = runTest {
        val vm = AcceptanceViewModel(productRepo, api)

        vm.addItem(barcode = "4607001234567", name = "A")
        vm.addItem(barcode = "4607001234567", name = "B")
        val secondId = vm.state.value.items[1].id
        val firstId = vm.state.value.items[0].id

        vm.updateQuantity(secondId, 3.0)
        vm.removeItem(firstId)

        val state = vm.state.value
        assertEquals(1, state.items.size)
        assertEquals(secondId, state.items[0].id)
        assertEquals(3.0, state.items[0].quantity, 0.0)
    }

    @Test
    fun `submit with empty items sets validation error and skips api call`() = runTest {
        val vm = AcceptanceViewModel(productRepo, api)

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertFalse(state.submitted)
        assertEquals("Добавьте хотя бы один товар", state.error)
        coVerify(exactly = 0) { api.sendAcceptance(any()) }
    }

    @Test
    fun `submit with non-success response keeps items and stores error code`() = runTest {
        coEvery { api.sendAcceptance(any()) } returns Response.error(
            500,
            "fail".toResponseBody("text/plain".toMediaType())
        )
        val vm = AcceptanceViewModel(productRepo, api)
        vm.addItem(barcode = "4607001234567", name = "Вода", price = 129.0)

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertFalse(state.submitted)
        assertEquals(1, state.items.size)
        assertEquals("Ошибка отправки: 500", state.error)
    }

    @Test
    fun `submit success clears items and marks submitted`() = runTest {
        coEvery { api.sendAcceptance(any()) } returns Response.success(Unit)
        val vm = AcceptanceViewModel(productRepo, api)
        vm.addItem(barcode = "4607001234567", name = "Вода", price = 129.0)

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertTrue(state.submitted)
        assertTrue(state.items.isEmpty())
        assertNull(state.error)
        coVerify(exactly = 1) { api.sendAcceptance(any()) }
    }
}
