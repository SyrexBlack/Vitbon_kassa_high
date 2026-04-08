package com.vitbon.kkm.features.statuses.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitbon.kkm.features.statuses.domain.StatusChecker
import com.vitbon.kkm.features.statuses.domain.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val statusChecker: StatusChecker
) : ViewModel() {
    val status: StateFlow<SystemStatus> = statusChecker.status

    fun refresh() {
        viewModelScope.launch {
            statusChecker.check()
        }
    }
}
