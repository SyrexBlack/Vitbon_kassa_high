package com.vitbon.kkm.core.features

import android.content.SharedPreferences
import com.vitbon.kkm.data.remote.dto.LoginFeaturesDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureManager @Inject constructor(
    private val prefs: SharedPreferences
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
    }

    /**
     * Деактивировать модуль.
     */
    fun disable(flag: FeatureFlag) {
        val current = _enabledFlags.value.toMutableSet()
        current.remove(flag)
        _enabledFlags.value = current
        persist(flag, false)
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
     * Асинхронная проверка без сетевых сайд-эффектов.
     */
    suspend fun isEnabled(flag: FeatureFlag): Boolean {
        return isEnabledSync(flag)
    }

    fun applyFeatures(features: LoginFeaturesDto) {
        val flags = buildSet {
            if (features.egaisEnabled) add(FeatureFlag.EGAAIS_ENABLED)
            if (features.chaseznakEnabled) add(FeatureFlag.CHASEZNAK_ENABLED)
            if (features.acquiringEnabled) add(FeatureFlag.ACQUIRING_ENABLED)
            if (features.sbpEnabled) add(FeatureFlag.SBP_ENABLED)
        }

        _enabledFlags.value = flags
        val editor = prefs.edit()
        FeatureFlag.entries.forEach { flag ->
            editor.putBoolean(flagKey(flag), flag in flags)
        }
        editor.apply()
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

}
