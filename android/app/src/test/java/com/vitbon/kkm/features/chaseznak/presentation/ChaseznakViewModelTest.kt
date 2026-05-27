package com.vitbon.kkm.features.chaseznak.presentation

import com.vitbon.kkm.features.chaseznak.domain.ChaseznakRepository
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakResult
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakValidation
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChaseznakViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ChaseznakRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onScan stores validation error and does not create sellable item`() = runTest {
        coEvery { repository.validateCode("010460123456789021ABC123456789") } returns ChaseznakValidation(
            barcode = "010460123456789021ABC123456789",
            status = ChaseznakStatus.ERROR,
            productName = null,
            expiryDate = null,
            message = "Интеграция не настроена"
        )
        val vm = ChaseznakViewModel(repository)

        vm.onScan("010460123456789021ABC123456789")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isValidating)
        assertEquals(1, state.items.size)
        assertEquals(ChaseznakStatus.ERROR, state.items.single().status)
    }

    @Test
    fun `sellAll keeps items and exposes error when disposal fails`() = runTest {
        val code = "010460123456789021ABC123456789"
        coEvery { repository.validateCode(code) } returns ChaseznakValidation(
            barcode = code,
            status = ChaseznakStatus.OK,
            productName = "Товар ЧЗ",
            expiryDate = null,
            message = null
        )
        coEvery { repository.sell(code, any()) } returns ChaseznakResult.Error(
            status = ChaseznakStatus.ERROR,
            message = "Ошибка выбытия: 501"
        )
        val vm = ChaseznakViewModel(repository)
        var completed = false

        vm.onScan(code)
        advanceUntilIdle()
        vm.sellAll { completed = true }
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSelling)
        assertFalse(completed)
        assertEquals(1, state.items.size)
        assertEquals("Ошибка выбытия: 501", state.error)
        assertTrue(state.items.all { it.status == ChaseznakStatus.OK })
        coVerify(exactly = 1) { repository.sell(code, any()) }
    }
}