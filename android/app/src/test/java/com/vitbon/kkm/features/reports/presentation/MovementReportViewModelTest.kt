package com.vitbon.kkm.features.reports.presentation

import com.vitbon.kkm.features.reports.domain.ReportLoadException
import com.vitbon.kkm.features.reports.domain.ReportsUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val useCase = mockk<ReportsUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load stores error and keeps report empty when movement report fails`() = runTest {
        coEvery { useCase.getMovementReport(any(), any()) } throws ReportLoadException("Не удалось загрузить отчёт движения")

        val viewModel = MovementReportViewModel(useCase)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.report)
        assertEquals("Не удалось загрузить отчёт движения", state.error)
    }
}