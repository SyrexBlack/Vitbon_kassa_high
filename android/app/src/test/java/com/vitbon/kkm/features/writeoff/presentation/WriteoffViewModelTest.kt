package com.vitbon.kkm.features.writeoff.presentation

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
class WriteoffViewModelTest {

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
    fun `add item assigns deterministic ids`() = runTest {
        val vm = WriteoffViewModel(api)

        vm.addItem(barcode = "4607001234567", name = "A", quantity = 1.0)
        vm.addItem(barcode = "4607001234568", name = "B", quantity = 2.0)

        val state = vm.state.value
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
    }

    @Test
    fun `remove affects only targeted id even with same barcode`() = runTest {
        val vm = WriteoffViewModel(api)

        vm.addItem(barcode = "4607001234567", name = "A", quantity = 1.0)
        vm.addItem(barcode = "4607001234567", name = "B", quantity = 2.0)
        val firstId = vm.state.value.items[0].id
        val secondId = vm.state.value.items[1].id

        vm.remove(firstId)

        val state = vm.state.value
        assertEquals(1, state.items.size)
        assertEquals(secondId, state.items.first().id)
    }

    @Test
    fun `submit with empty items sets validation error and skips api`() = runTest {
        val vm = WriteoffViewModel(api)
        vm.setReason("Бой")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertFalse(state.submitted)
        assertEquals("Добавьте хотя бы один товар", state.error)
        coVerify(exactly = 0) { api.sendWriteoff(any()) }
    }

    @Test
    fun `submit with blank reason sets validation error and skips api`() = runTest {
        val vm = WriteoffViewModel(api)
        vm.addItem(barcode = "4607001234567", name = "Вода", quantity = 1.0)

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertFalse(state.submitted)
        assertEquals("Укажите причину списания", state.error)
        coVerify(exactly = 0) { api.sendWriteoff(any()) }
    }

    @Test
    fun `submit non-success keeps items and sets error`() = runTest {
        coEvery { api.sendWriteoff(any()) } returns Response.error(
            503,
            "fail".toResponseBody("text/plain".toMediaType())
        )
        val vm = WriteoffViewModel(api)
        vm.addItem(barcode = "4607001234567", name = "Вода", quantity = 1.0)
        vm.setReason("Бой")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertFalse(state.submitted)
        assertEquals(1, state.items.size)
        assertEquals("Ошибка отправки: 503", state.error)
    }

    @Test
    fun `submit success resets state`() = runTest {
        coEvery { api.sendWriteoff(any()) } returns Response.success(Unit)
        val vm = WriteoffViewModel(api)
        vm.addItem(barcode = "4607001234567", name = "Вода", quantity = 1.0)
        vm.setReason("Бой")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.submitting)
        assertTrue(state.submitted)
        assertTrue(state.items.isEmpty())
        assertEquals("", state.reason)
        assertNull(state.error)
        coVerify(exactly = 1) { api.sendWriteoff(any()) }
    }
}
