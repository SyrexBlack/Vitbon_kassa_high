package com.vitbon.kkm.core.features

import android.content.SharedPreferences
import com.vitbon.kkm.data.remote.dto.LoginFeaturesDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureManagerTest {

    private val prefs = InMemorySharedPreferences()

    @Test
    fun `isEnabled returns cached value`() = runBlocking {
        prefs.edit().putBoolean("feature_${FeatureFlag.EGAAIS_ENABLED.name}", true).apply()
        val manager = FeatureManager(prefs)

        val enabled = manager.isEnabled(FeatureFlag.EGAAIS_ENABLED)

        assertTrue(enabled)
    }

    @Test
    fun `isEnabled returns false when flag missing in cache`() = runBlocking {
        val manager = FeatureManager(prefs)

        val enabled = manager.isEnabled(FeatureFlag.CHASEZNAK_ENABLED)

        assertFalse(enabled)
    }

    @Test
    fun `applyFeatures applies backend feature flags from login response`() {
        val manager = FeatureManager(prefs)

        manager.applyFeatures(
            LoginFeaturesDto(
                egaisEnabled = true,
                chaseznakEnabled = false,
                acquiringEnabled = true,
                sbpEnabled = true
            )
        )

        assertTrue(manager.isEnabledSync(FeatureFlag.EGAAIS_ENABLED))
        assertFalse(manager.isEnabledSync(FeatureFlag.CHASEZNAK_ENABLED))
        assertTrue(manager.isEnabledSync(FeatureFlag.ACQUIRING_ENABLED))
        assertTrue(manager.isEnabledSync(FeatureFlag.SBP_ENABLED))
        assertEquals(
            setOf(
                FeatureFlag.EGAAIS_ENABLED,
                FeatureFlag.ACQUIRING_ENABLED,
                FeatureFlag.SBP_ENABLED
            ),
            manager.enabledFlags.value
        )
    }

    @Test
    fun `enable and disable update cache`() {
        val manager = FeatureManager(prefs)

        manager.enable(FeatureFlag.SBP_ENABLED)
        assertTrue(manager.isEnabledSync(FeatureFlag.SBP_ENABLED))

        manager.disable(FeatureFlag.SBP_ENABLED)
        assertFalse(manager.isEnabledSync(FeatureFlag.SBP_ENABLED))
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        val value = data[key]
        return if (value is String?) value else defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = data[key]
        return if (value is MutableSet<*>) value as MutableSet<String> else defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    private class Editor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                data.clear()
                clearRequested = false
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
            pending.clear()
        }
    }
}
