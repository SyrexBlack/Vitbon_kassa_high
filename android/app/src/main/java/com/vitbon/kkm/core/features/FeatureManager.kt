package com.vitbon.kkm.core.features

import android.content.SharedPreferences
import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FeatureManager"

@Singleton
class FeatureManager @Inject constructor(
    private val prefs: SharedPreferences,
    private val api: VitbonApi
) {
    private val _enabledFlags = MutableStateFlow<Set<FeatureFlag>>(emptySet())
    val enabledFlags: StateFlow<Set<FeatureFlag>> = _enabledFlags.asStateFlow()

    init {
        // Загрузить кеш из SharedPrefs при старте
        loadFromCache()
    }

    /**
     * Активировать модуль. Вызывается после успешной проверки remote key.
     * Без переустановки приложения.
     */
    fun enable(flag: FeatureFlag) {
        val current = _enabledFlags.value.toMutableSet()
        current.add(flag)
        _enabledFlags.value = current
        persist(flag, true)
        Log.i(TAG, "Feature enabled: $flag")
    }

    /**
     * Деактивировать модуль.
     */
    fun disable(flag: FeatureFlag) {
        val current = _enabledFlags.value.toMutableSet()
        current.remove(flag)
        _enabledFlags.value = current
        persist(flag, false)
        Log.i(TAG, "Feature disabled: $flag")
    }

    /**
     * Синхронная проверка — использует кеш SharedPrefs.
     * Без сетевого запроса.
     */
    fun isEnabledSync(flag: FeatureFlag): Boolean {
        val key = flagKey(flag)
        return prefs.getBoolean(key, false)
    }

    /**
     * Асинхронная проверка — сначала кеш, затем сервер.
     */
    suspend fun isEnabled(flag: FeatureFlag): Boolean {
        // 1. Кеш
        if (isEnabledSync(flag)) return true

        // 2. Запрос на сервер (при запуске приложения)
        try {
            val response = api.getStatuses()
            if (response.isSuccessful) {
                val body = response.body()!!
                // Сервер возвращает список активных флагов в заголовке или теле
                // Пока считаем что флаги приходят в статусе
                Log.d(TAG, "Feature flags from server: ${body}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch feature flags", e)
        }

        return isEnabledSync(flag)
    }

    /**
     * Загрузить все флаги из SharedPrefs в StateFlow.
     */
    private fun loadFromCache() {
        val flags = FeatureFlag.entries.filter { isEnabledSync(it) }.toSet()
        _enabledFlags.value = flags
    }

    private fun persist(flag: FeatureFlag, enabled: Boolean) {
        prefs.edit().putBoolean(flagKey(flag), enabled).apply()
    }

    private fun flagKey(flag: FeatureFlag) = "feature_${flag.name}"

    companion object {
        /**
         * Активировать все модули по remote key.
         * Вызывается после верификации ключа от backend.
         */
        fun activateAll(
            manager: FeatureManager,
            egais: Boolean,
            chaseznak: Boolean,
            acquiring: Boolean,
            sbp: Boolean
        ) {
            if (egais) manager.enable(FeatureFlag.EGAAIS_ENABLED)
            if (chaseznak) manager.enable(FeatureFlag.CHASEZNAK_ENABLED)
            if (acquiring) manager.enable(FeatureFlag.ACQUIRING_ENABLED)
            if (sbp) manager.enable(FeatureFlag.SBP_ENABLED)
        }
    }
}
