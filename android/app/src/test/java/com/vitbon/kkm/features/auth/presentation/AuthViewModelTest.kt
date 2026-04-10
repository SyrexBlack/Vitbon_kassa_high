package com.vitbon.kkm.features.auth.presentation

import com.vitbon.kkm.features.auth.domain.AuthResult
import com.vitbon.kkm.features.auth.domain.AuthUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
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
    fun `local success plus backend invalid sets non-blocking backend warning`() = runTest {
        val success = AuthResult.Success(
            cashier = com.vitbon.kkm.features.auth.domain.AuthenticatedCashier(
                id = "cashier-demo-1",
                name = "Демо Кассир",
                role = com.vitbon.kkm.features.auth.domain.CashierRole.CASHIER
            )
        )
        coEvery { authUseCase.authenticate("1111") } returns success
        coEvery { authUseCase.validateWithBackendBestEffort("1111") } returns
            "Локальный вход выполнен, но сервер не подтвердил авторизацию"

        val vm = AuthViewModel(authUseCase)

        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.result is AuthResult.Success)
        assertEquals(
            "Локальный вход выполнен, но сервер не подтвердил авторизацию",
            state.backendWarning
        )
        coVerify(exactly = 1) { authUseCase.validateWithBackendBestEffort("1111") }
    }

    @Test
    fun `offline backend path keeps success and no warning`() = runTest {
        val success = AuthResult.Success(
            cashier = com.vitbon.kkm.features.auth.domain.AuthenticatedCashier(
                id = "cashier-demo-1",
                name = "Демо Кассир",
                role = com.vitbon.kkm.features.auth.domain.CashierRole.CASHIER
            )
        )
        coEvery { authUseCase.authenticate("1111") } returns success
        coEvery { authUseCase.validateWithBackendBestEffort("1111") } returns null

        val vm = AuthViewModel(authUseCase)

        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.result is AuthResult.Success)
        assertNull(state.backendWarning)
        coVerify(exactly = 1) { authUseCase.validateWithBackendBestEffort("1111") }
    }
}
