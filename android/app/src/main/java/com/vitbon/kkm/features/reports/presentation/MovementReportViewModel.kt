package com.vitbon.kkm.features.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovementState(
    val period: String = "day",
    val report: MovementReport? = null,
    val isLoading: Boolean = false
)

data class MovementReport(
    val openingStock: Int,
    val income: Int,
    val sales: Int,
    val returns: Int,
    val writeoff: Int,
    val closingStock: Int,
    val items: List<MovementItem>
)

data class MovementItem(
    val name: String,
    val income: Int,
    val sales: Int,
    val balance: Int
)

@HiltViewModel
class MovementReportViewModel @Inject constructor() : ViewModel() {
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
            _state.update {
                it.copy(
                    isLoading = false,
                    report = MovementReport(
                        openingStock = 150,
                        income = 50,
                        sales = 30,
                        returns = 2,
                        writeoff = 1,
                        closingStock = 171,
                        items = listOf(
                            MovementItem("Товар А", 20, 15, 105),
                            MovementItem("Товар Б", 30, 15, 66)
                        )
                    )
                )
            }
        }
    }
}
