package com.vitbon.kkm.features.auth.presentation

import com.vitbon.kkm.features.auth.domain.AuthResult
import com.vitbon.kkm.features.auth.domain.AuthUseCase
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authUseCase = mockk<AuthUseCase>()

    private fun mockRootRiskGuard(): RootRiskGuard {
        val guard = mockk<RootRiskGuard>()
        val unblockedFlow = kotlinx.coroutines.flow.MutableStateFlow(AppBlockingState.Unblocked)
        every { guard.blockingState } returns unblockedFlow
        return guard
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful auth stores result without backend warning`() = runTest {
        val success = AuthResult.Success(
            cashier = com.vitbon.kkm.features.auth.domain.AuthenticatedCashier(
                id = "cashier-demo-1",
                name = "Демо Кассир",
                role = com.vitbon.kkm.features.auth.domain.CashierRole.CASHIER
            )
        )
        coEvery { authUseCase.authenticate("1111") } returns success

        val vm = AuthViewModel(authUseCase, mockRootRiskGuard())

        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        vm.appendDigit("1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.result is AuthResult.Success)
        assertEquals(null, state.backendWarning)
        coVerify(exactly = 1) { authUseCase.authenticate("1111") }
    }
}
