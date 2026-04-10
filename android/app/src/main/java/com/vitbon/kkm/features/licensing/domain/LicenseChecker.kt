package com.vitbon.kkm.features.licensing.domain

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.LicenseCheckRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LicenseChecker"
private const val PREFS_NAME = "license_prefs"
private const val KEY_LAST_CHECK = "last_check_ts"
private const val KEY_GRACE_UNTIL = "grace_until_ts"
private const val KEY_LICENSE_STATUS = "license_status"
private const val GRACE_PERIOD_DAYS = 7L
private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val CHECK_INTERVAL_MS = DAY_MS  // 24 hours

@Singleton
class LicenseChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VitbonApi,
    private val prefs: SharedPreferences
) {
    private val _status = MutableStateFlow<LicenseStatus>(LicenseStatus.Active)
    val status: StateFlow<LicenseStatus> = _status.asStateFlow()

    private val _blockingState = MutableStateFlow<AppBlockingState>(AppBlockingState.Unblocked)
    val blockingState: StateFlow<AppBlockingState> = _blockingState.asStateFlow()

    private val _deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    /** Получить deviceId для отправки на сервер */
    fun getDeviceId(): String = _deviceId

    /**
     * Проверка лицензии. Вызывается:
     * 1. При старте приложения
     * 2. Каждые 24ч при наличии сети
     */
    suspend fun check(): LicenseStatus {
        return try {
            val response = api.checkLicense(LicenseCheckRequestDto(_deviceId))
            if (response.isSuccessful) {
                val body = response.body()!!
                val now = System.currentTimeMillis()

                when (body.status) {
                    "ACTIVE" -> {
                        prefs.edit()
                            .putLong(KEY_LAST_CHECK, now)
                            .putString(KEY_LICENSE_STATUS, "ACTIVE")
                            .remove(KEY_GRACE_UNTIL)
                            .apply()
                        _status.value = LicenseStatus.Active
                        _blockingState.value = AppBlockingState.Unblocked
                        Log.d(TAG, "License: ACTIVE")
                        LicenseStatus.Active
                    }
                    "EXPIRED" -> {
                        // Проверяем grace period
                        handleExpired(now, body.graceUntil)
                    }
                    "GRACE_PERIOD" -> {
                        handleGracePeriod(now, body.graceUntil)
                    }
                    else -> {
                        // При ошибке сервера — считать активным (не блокировать)
                        Log.w(TAG, "Unknown license status: ${body.status}")
                        LicenseStatus.Active
                    }
                }
            } else {
                // Сетевая ошибка — проверить grace period
                Log.w(TAG, "License check failed: ${response.code()}")
                checkGraceExpired()
            }
        } catch (e: Exception) {
            Log.w(TAG, "License check exception: ${e.message}")
            // При исключении (нет сети) — проверить grace period
            checkGraceExpired()
        }
    }

    private fun handleExpired(now: Long, graceUntil: Long?): LicenseStatus {
        val graceTs = graceUntil ?: (now + GRACE_PERIOD_DAYS * DAY_MS)
        val daysLeft = calculateDaysLeft(graceTs, now)

        prefs.edit()
            .putLong(KEY_LAST_CHECK, now)
            .putLong(KEY_GRACE_UNTIL, graceTs)
            .putString(KEY_LICENSE_STATUS, "GRACE_PERIOD")
            .apply()

        if (daysLeft > 0) {
            _status.value = LicenseStatus.GracePeriod(daysLeft)
            _blockingState.value = AppBlockingState.Unblocked
            Log.d(TAG, "License: GRACE_PERIOD, daysLeft=$daysLeft")
            return _status.value
        } else {
            _status.value = LicenseStatus.Expired
            _blockingState.value = AppBlockingState.Blocked("Лицензия просрочена. Обратитесь в поддержку.")
            Log.w(TAG, "License: EXPIRED, blocked")
            return _status.value
        }
    }

    private fun handleGracePeriod(now: Long, graceUntil: Long?): LicenseStatus {
        val graceTs = graceUntil ?: (now + GRACE_PERIOD_DAYS * DAY_MS)
        val daysLeft = calculateDaysLeft(graceTs, now)

        prefs.edit()
            .putLong(KEY_LAST_CHECK, now)
            .putLong(KEY_GRACE_UNTIL, graceTs)
            .putString(KEY_LICENSE_STATUS, "GRACE_PERIOD")
            .apply()

        if (daysLeft > 0) {
            _status.value = LicenseStatus.GracePeriod(daysLeft)
            _blockingState.value = AppBlockingState.Unblocked
            return _status.value
        } else {
            _status.value = LicenseStatus.Expired
            _blockingState.value = AppBlockingState.Blocked("Лицензия просрочена. Обратитесь в поддержку.")
            return _status.value
        }
    }

    private fun checkGraceExpired(): LicenseStatus {
        val graceUntil = prefs.getLong(KEY_GRACE_UNTIL, 0L)
        val now = System.currentTimeMillis()
        val daysLeft = calculateDaysLeft(graceUntil, now)

        return if (graceUntil == 0L) {
            // Никогда не проверяли — запустить grace period
            val newGrace = now + GRACE_PERIOD_DAYS * DAY_MS
            prefs.edit().putLong(KEY_GRACE_UNTIL, newGrace).apply()
            _status.value = LicenseStatus.GracePeriod(7)
            _blockingState.value = AppBlockingState.Unblocked
            LicenseStatus.GracePeriod(7)
        } else if (daysLeft > 0) {
            _status.value = LicenseStatus.GracePeriod(daysLeft)
            _blockingState.value = AppBlockingState.Unblocked
            LicenseStatus.GracePeriod(daysLeft)
        } else {
            _status.value = LicenseStatus.Expired
            _blockingState.value = AppBlockingState.Blocked("Лицензия просрочена. Обратитесь в поддержку.")
            LicenseStatus.Expired
        }
    }

    private fun calculateDaysLeft(graceUntil: Long, now: Long): Int {
        val millisLeft = graceUntil - now
        if (millisLeft <= 0L) return 0
        return ((millisLeft + DAY_MS - 1) / DAY_MS).toInt()
    }

    /** Должен ли показываться экран блокировки */
    fun isBlocked(): Boolean = _blockingState.value is AppBlockingState.Blocked

    /** Проверка по расписанию (24ч) */
    fun shouldCheck(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }
}
