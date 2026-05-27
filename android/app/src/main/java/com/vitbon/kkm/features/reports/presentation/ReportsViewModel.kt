package com.vitbon.kkm.features.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.reports.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ReportsState(
    val period: String = "shift",
    val report: SalesReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(private val useCase: ReportsUseCase) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init { load() }

    fun setPeriod(period: String) {
        _state.update { it.copy(period = period) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val period = _state.value.period
            val (from, to) = getPeriodRange(period)
            try {
                val report = useCase.getSalesReport(period, from, to)
                _state.update { it.copy(report = report, isLoading = false, error = null) }
            } catch (error: ReportLoadException) {
                _state.update { it.copy(report = null, isLoading = false, error = error.message) }
            }
        }
    }

    private fun getPeriodRange(period: String): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        return when (period) {
            "shift" -> now - 8 * 3600 * 1000L to now  // последние 8ч
            "day" -> cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis to now
            "week" -> cal.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis to now
            "month" -> cal.apply { add(Calendar.MONTH, -1) }.timeInMillis to now
            else -> now - 86400 * 1000L to now
        }
    }
}
