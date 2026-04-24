package com.vitbon.kkm.features.cashdrawer.presentation

import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.cashdrawer.domain.CashDrawerUseCase
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CashDrawerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val useCase = mockk<CashDrawerUseCase>()
    private val authUseCase = mockk<AuthUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authUseCase.isEmergencySessionActive() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit denies cash in for cashier role`() = runTest {
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER
        every { useCase.canCashIn(CashierRole.CASHIER) } returns false

        val vm = CashDrawerViewModel(useCase, authUseCase)
        vm.setType("in")
        vm.setAmount("100")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertEquals("Операция запрещена для текущей роли", state.error)
        coVerify(exactly = 0) { useCase.cashIn(any(), any()) }
    }

    @Test
    fun `submit denies cash out for cashier role`() = runTest {
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.CASHIER
        every { useCase.canCashOut(CashierRole.CASHIER) } returns false

        val vm = CashDrawerViewModel(useCase, authUseCase)
        vm.setType("out")
        vm.setAmount("100")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertEquals("Операция запрещена для текущей роли", state.error)
        coVerify(exactly = 0) { useCase.cashOut(any(), any()) }
    }

    @Test
    fun `submit denies during active emergency session`() = runTest {
        every { authUseCase.getCurrentCashierRole() } returns CashierRole.ADMIN
        every { authUseCase.isEmergencySessionActive() } returns true
        every { useCase.canCashIn(CashierRole.ADMIN) } returns true

        val vm = CashDrawerViewModel(useCase, authUseCase)
        vm.setType("in")
        vm.setAmount("100")

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSubmitting)
        assertEquals("Операция запрещена для текущей роли", state.error)
        coVerify(exactly = 0) { useCase.cashIn(any(), any()) }
        coVerify(exactly = 0) { useCase.cashOut(any(), any()) }
    }
}
