package com.vitbon.kkm.features.shift.presentation

import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.shift.domain.ShiftResult
import com.vitbon.kkm.features.shift.domain.ShiftStatus
import com.vitbon.kkm.features.shift.domain.ShiftUseCase
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
class ShiftViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val shiftUseCase = mockk<ShiftUseCase>()
    private val authUseCase = mockk<AuthUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `closeShift closes currently open shift and updates state to CLOSED`() = runTest {
        coEvery { shiftUseCase.findOpenShiftId() } returns "shift-open-1"
        coEvery { shiftUseCase.closeShift("shift-open-1") } returns ShiftResult.Success("shift-open-1")

        val vm = ShiftViewModel(shiftUseCase, authUseCase)

        vm.closeShift()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShiftStatus.CLOSED, state.shiftStatus)
        assertFalse(state.isLoading)
        assertTrue(state.error == null)

        coVerify(exactly = 1) { shiftUseCase.findOpenShiftId() }
        coVerify(exactly = 1) { shiftUseCase.closeShift("shift-open-1") }
    }

    @Test
    fun `closeShift without open shift sets user-facing error`() = runTest {
        coEvery { shiftUseCase.findOpenShiftId() } returns null

        val vm = ShiftViewModel(shiftUseCase, authUseCase)

        vm.closeShift()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Нет открытой смены для закрытия", state.error)

        coVerify(exactly = 1) { shiftUseCase.findOpenShiftId() }
        coVerify(exactly = 0) { shiftUseCase.closeShift(any()) }
    }

    @Test
    fun `closeShift fiscal failure keeps shift open and exposes error`() = runTest {
        coEvery { shiftUseCase.checkShiftStatus() } returns ShiftStatus.OPEN
        coEvery { shiftUseCase.findOpenShiftId() } returns "shift-open-2"
        coEvery { shiftUseCase.closeShift("shift-open-2") } returns ShiftResult.Error(
            code = 400,
            message = "fiscal busy"
        )

        val vm = ShiftViewModel(shiftUseCase, authUseCase)

        vm.checkStatus()
        advanceUntilIdle()
        vm.closeShift()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShiftStatus.OPEN, state.shiftStatus)
        assertFalse(state.isLoading)
        assertEquals("400: fiscal busy", state.error)

        coVerify(exactly = 1) { shiftUseCase.checkShiftStatus() }
        coVerify(exactly = 1) { shiftUseCase.findOpenShiftId() }
        coVerify(exactly = 1) { shiftUseCase.closeShift("shift-open-2") }
    }
}
