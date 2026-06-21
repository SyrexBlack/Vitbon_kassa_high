package com.vitbon.kkm.features.licensing.domain

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.vitbon.kkm.BuildConfig
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.LicenseCheckRequestDto
import com.vitbon.kkm.data.security.PrefsMigration
import com.vitbon.kkm.di.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LicenseChecker"
private const val GRACE_PERIOD_DAYS = 7L
private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val CHECK_INTERVAL_MS = DAY_MS  // 24 hours
private const val LICENSE_VERIFICATION_FAILED_REASON = "Не удалось подтвердить статус лицензии. Подключите сеть или обратитесь в поддержку."

@Singleton
class LicenseChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VitbonApi,
    private val plainPrefs: SharedPreferences,
    @SecurePrefs private val securePrefs: SharedPreferences
) {
    private val _status = MutableStateFlow<LicenseStatus>(LicenseStatus.Active)
    val status: StateFlow<LicenseStatus> = _status.asStateFlow()

    private val _blockingState = MutableStateFlow<AppBlockingState>(AppBlockingState.Unblocked)
    val blockingState: StateFlow<AppBlockingState> = _blockingState.asStateFlow()

    init {
        PrefsMigration.migrateLicenseData(securePrefs, plainPrefs)
    }

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
                        securePrefs.edit()
                            .putLong(PrefsMigration.KEY_LAST_CHECK, now)
                            .putString(PrefsMigration.KEY_LICENSE_STATUS, "ACTIVE")
                            .remove(PrefsMigration.KEY_GRACE_UNTIL)
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
                    "UNLICENSED" -> {
                        securePrefs.edit()
                            .putLong(PrefsMigration.KEY_LAST_CHECK, now)
                            .putString(PrefsMigration.KEY_LICENSE_STATUS, "UNLICENSED")
                            .remove(PrefsMigration.KEY_GRACE_UNTIL)
                            .apply()
                        _status.value = LicenseStatus.Expired
                        _blockingState.value = AppBlockingState.Blocked("Устройство не лицензировано. Обратитесь в поддержку.")
                        Log.w(TAG, "License: UNLICENSED, blocked")
                        LicenseStatus.Expired
                    }
                    else -> {
                        // Неизвестный ответ сервера не должен silently разблокировать кассу.
                        Log.w(TAG, "Unknown license status: ${body.status}")
                        _status.value = LicenseStatus.Error("Неизвестный статус лицензии: ${body.status}")
                        _blockingState.value = AppBlockingState.Blocked(LICENSE_VERIFICATION_FAILED_REASON)
                        _status.value
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

        securePrefs.edit()
            .putLong(PrefsMigration.KEY_LAST_CHECK, now)
            .putLong(PrefsMigration.KEY_GRACE_UNTIL, graceTs)
            .putString(PrefsMigration.KEY_LICENSE_STATUS, "GRACE_PERIOD")
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

        securePrefs.edit()
            .putLong(PrefsMigration.KEY_LAST_CHECK, now)
            .putLong(PrefsMigration.KEY_GRACE_UNTIL, graceTs)
            .putString(PrefsMigration.KEY_LICENSE_STATUS, "GRACE_PERIOD")
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
        val graceUntil = securePrefs.getLong(PrefsMigration.KEY_GRACE_UNTIL, 0L)
        val lastKnownStatus = securePrefs.getString(PrefsMigration.KEY_LICENSE_STATUS, null)
        val now = System.currentTimeMillis()
        val daysLeft = calculateDaysLeft(graceUntil, now)

        return if (graceUntil > 0L && daysLeft > 0) {
            _status.value = LicenseStatus.GracePeriod(daysLeft)
            _blockingState.value = AppBlockingState.Unblocked
            LicenseStatus.GracePeriod(daysLeft)
        } else if (graceUntil > 0L) {
            _status.value = LicenseStatus.Expired
            _blockingState.value = AppBlockingState.Blocked("Лицензия просрочена. Обратитесь в поддержку.")
            LicenseStatus.Expired
        } else if (lastKnownStatus == "ACTIVE") {
            val newGrace = now + GRACE_PERIOD_DAYS * DAY_MS
            val graceDays = calculateDaysLeft(newGrace, now)
            securePrefs.edit()
                .putLong(PrefsMigration.KEY_LAST_CHECK, now)
                .putLong(PrefsMigration.KEY_GRACE_UNTIL, newGrace)
                .putString(PrefsMigration.KEY_LICENSE_STATUS, "GRACE_PERIOD")
                .apply()
            _status.value = LicenseStatus.GracePeriod(graceDays)
            _blockingState.value = AppBlockingState.Unblocked
            LicenseStatus.GracePeriod(graceDays)
        } else if (BuildConfig.DEBUG) {
            _status.value = LicenseStatus.Active
            _blockingState.value = AppBlockingState.Unblocked
            LicenseStatus.Active
        } else {
            _status.value = LicenseStatus.Error(LICENSE_VERIFICATION_FAILED_REASON)
            _blockingState.value = AppBlockingState.Blocked(LICENSE_VERIFICATION_FAILED_REASON)
            _status.value
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
        val lastCheck = securePrefs.getLong(PrefsMigration.KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }
}
