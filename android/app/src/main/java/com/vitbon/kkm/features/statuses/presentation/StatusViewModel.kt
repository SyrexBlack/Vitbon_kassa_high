package com.vitbon.kkm.features.statuses.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.statuses.domain.StatusChecker
import com.vitbon.kkm.features.statuses.domain.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STATUS_REFRESH_INTERVAL_MS = 30_000L

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val statusChecker: StatusChecker
) : ViewModel() {
    val status: StateFlow<SystemStatus> = statusChecker.status

    init {
        viewModelScope.launch {
            while (isActive) {
                statusChecker.check()
                delay(STATUS_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            statusChecker.check()
        }
    }
}
