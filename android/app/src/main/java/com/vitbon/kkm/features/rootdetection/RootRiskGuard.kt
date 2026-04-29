package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import com.vitbon.kkm.features.rootdetection.domain.RootPolicyEnforcer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_CACHED_RESULT = "root_risk_cached"
private const val KEY_CACHED_TS = "root_risk_ts"

@Singleton
class RootRiskGuard @Inject constructor(
    private val context: Context,
    private val detector: RootDetector,
    private val prefs: SharedPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _blockingState = MutableStateFlow<AppBlockingState>(AppBlockingState.Unblocked)
    val blockingState: StateFlow<AppBlockingState> = _blockingState.asStateFlow()

    init {
        loadCachedState()
        triggerAsyncCheck()
    }

    private fun loadCachedState() {
        val cachedResult = prefs.getString(KEY_CACHED_RESULT, null)
        if (cachedResult != null) {
            val checkResult = when (cachedResult) {
                "CLEAN" -> RootCheckResult.Clean
                "DETECTED" -> {
                    RootCheckResult.Detected(
                        listOf(RootIndicator("cached", "root detected at ${prefs.getLong(KEY_CACHED_TS, 0L)}"))
                    )
                }
                else -> null
            }
            if (checkResult != null) {
                _blockingState.value = RootPolicyEnforcer.toBlockingState(checkResult)
            }
        }
    }

    private fun triggerAsyncCheck() {
        scope.launch {
            val result = detector.check(context)
            _blockingState.value = RootPolicyEnforcer.toBlockingState(result)
            persistResult(result)
        }
    }

    private fun persistResult(result: RootCheckResult) {
        val resultStr = when (result) {
            is RootCheckResult.Clean -> "CLEAN"
            is RootCheckResult.Detected -> "DETECTED"
        }
        prefs.edit()
            .putString(KEY_CACHED_RESULT, resultStr)
            .putLong(KEY_CACHED_TS, System.currentTimeMillis())
            .apply()
    }

    fun getCurrentBlockingState(): AppBlockingState = _blockingState.value
}