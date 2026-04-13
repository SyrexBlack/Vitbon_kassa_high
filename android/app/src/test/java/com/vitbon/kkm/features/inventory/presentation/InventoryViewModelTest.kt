package com.vitbon.kkm.features.inventory.presentation

import com.vitbon.kkm.data.remote.api.VitbonApi
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
class InventoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
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
    fun `submit with empty items sets validation error and skips api`() = runTest {
        val vm = InventoryViewModel(api)

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertFalse(state.submitted)
        assertEquals("Нет данных для инвентаризации", state.error)
        coVerify(exactly = 0) { api.sendInventory(any()) }
    }

    @Test
    fun `set actual updates targeted barcode`() = runTest {
        val vm = InventoryViewModel(api)
        vm.setItemsForTest(
            listOf(
                InventoryItem(barcode = "4607001234567", name = "Вода", accounted = 10.0, actual = 10.0),
                InventoryItem(barcode = "4607001234568", name = "Сок", accounted = 5.0, actual = 5.0)
            )
        )

        vm.setActual("4607001234568", 4.0)

        val state = vm.state.value
        assertEquals(10.0, state.items[0].actual, 0.0)
        assertEquals(4.0, state.items[1].actual, 0.0)
    }

    @Test
    fun `submit non-success keeps items and sets error`() = runTest {
        coEvery { api.sendInventory(any()) } returns Response.error(
            500,
            "fail".toResponseBody("text/plain".toMediaType())
        )
        val vm = InventoryViewModel(api)
        vm.setItemsForTest(
            listOf(
                InventoryItem(barcode = "4607001234567", name = "Вода", accounted = 10.0, actual = 8.0)
            )
        )

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertFalse(state.submitted)
        assertEquals(1, state.items.size)
        assertEquals("Ошибка отправки: 500", state.error)
    }

    @Test
    fun `submit success marks submitted and clears error`() = runTest {
        coEvery { api.sendInventory(any()) } returns Response.success(Unit)
        val vm = InventoryViewModel(api)
        vm.setItemsForTest(
            listOf(
                InventoryItem(barcode = "4607001234567", name = "Вода", accounted = 10.0, actual = 8.0)
            )
        )

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertTrue(state.submitted)
        assertNull(state.error)
        assertEquals(1, state.items.size)
        coVerify(exactly = 1) { api.sendInventory(any()) }
    }
}
