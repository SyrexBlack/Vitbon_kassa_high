package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.data.security.PrefsMigration
import com.vitbon.kkm.di.SecurePrefs
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

private const val ROOT_CHECK_PENDING_REASON = "Проверка безопасности устройства выполняется. Дождитесь завершения."

@Singleton
class RootRiskGuard @Inject constructor(
    private val context: Context,
    private val detector: RootDetector,
    private val plainPrefs: SharedPreferences,
    @SecurePrefs private val securePrefs: SharedPreferences,
    private val debugBypass: Boolean = false,
    private val asyncCheckEnabled: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _blockingState = MutableStateFlow<AppBlockingState>(
        if (debugBypass) AppBlockingState.Unblocked else AppBlockingState.Blocked(ROOT_CHECK_PENDING_REASON)
    )
    val blockingState: StateFlow<AppBlockingState> = _blockingState.asStateFlow()

    init {
        if (!debugBypass) {
            PrefsMigration.migrateRootRiskData(securePrefs, plainPrefs)
            loadCachedState()
            if (asyncCheckEnabled) {
                triggerAsyncCheck()
            }
        }
    }

    private fun loadCachedState() {
        val cachedResult = securePrefs.getString(PrefsMigration.KEY_ROOT_CACHED_RESULT, null)
        if (cachedResult != null) {
            val checkResult = when (cachedResult) {
                "CLEAN" -> null
                "DETECTED" -> {
                    RootCheckResult.Detected(
                        listOf(
                            RootIndicator(
                                "cached",
                                "root detected at ${securePrefs.getLong(PrefsMigration.KEY_ROOT_CACHED_TS, 0L)}"
                            )
                        )
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
        securePrefs.edit()
            .putString(PrefsMigration.KEY_ROOT_CACHED_RESULT, resultStr)
            .putLong(PrefsMigration.KEY_ROOT_CACHED_TS, System.currentTimeMillis())
            .apply()
    }

    fun getCurrentBlockingState(): AppBlockingState = _blockingState.value
}