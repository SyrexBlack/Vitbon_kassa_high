package com.vitbon.kkm.features.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.reports.domain.ReportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MovementState(
    val period: String = "day",
    val report: MovementReport? = null,
    val isLoading: Boolean = false
)

data class MovementReport(
    val openingStock: Double,
    val income: Double,
    val sales: Double,
    val returns: Double,
    val writeoff: Double,
    val closingStock: Double,
    val items: List<MovementItem>
)

data class MovementItem(
    val name: String,
    val income: Double,
    val sales: Double,
    val balance: Double
)

@HiltViewModel
class MovementReportViewModel @Inject constructor(
    private val reportsUseCase: ReportsUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(MovementState())
    val state: StateFlow<MovementState> = _state.asStateFlow()

    init {
        load()
    }

    fun setPeriod(period: String) {
        _state.update { it.copy(period = period) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val period = _state.value.period
            val since = resolveSince(period)
            val reportData = reportsUseCase.getMovementReport(period = period, since = since)
            val report = MovementReport(
                openingStock = reportData.openingStock,
                income = reportData.income,
                sales = reportData.sales,
                returns = reportData.returns,
                writeoff = reportData.writeoff,
                closingStock = reportData.closingStock,
                items = reportData.items.map {
                    MovementItem(
                        name = it.name,
                        income = it.income,
                        sales = it.sales,
                        balance = it.balance
                    )
                }
            )
            _state.update { it.copy(isLoading = false, report = report) }
        }
    }

    private fun resolveSince(period: String): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        return when (period) {
            "day" -> calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "week" -> now - 7L * 24 * 3600 * 1000
            "month" -> now - 30L * 24 * 3600 * 1000
            else -> now - 24L * 3600 * 1000
        }
    }
}
