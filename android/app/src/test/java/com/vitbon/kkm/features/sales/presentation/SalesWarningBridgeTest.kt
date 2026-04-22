package com.vitbon.kkm.features.sales.presentation

import android.content.SharedPreferences
import com.vitbon.kkm.core.features.FeatureFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SalesWarningBridgeTest {

    @Test
    fun `consumeBackendAuthWarning reads from prefs and clears key`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(BACKEND_AUTH_WARNING_KEY, "сервер недоступен").apply()

        val warning = consumeBackendAuthWarning(prefs)

        assertEquals("сервер недоступен", warning)
        assertNull(prefs.getString(BACKEND_AUTH_WARNING_KEY, null))
    }

    @Test
    fun `resolveBackendWarning returns direct warning and clears stale prefs key`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(BACKEND_AUTH_WARNING_KEY, "устаревшее").apply()

        val warning = resolveBackendWarning(
            warningMessage = "актуальное",
            prefs = prefs
        )

        assertEquals("актуальное", warning)
        assertNull(prefs.getString(BACKEND_AUTH_WARNING_KEY, null))
    }

    @Test
    fun `resolveBackendWarning consumes prefs warning when direct is missing`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(BACKEND_AUTH_WARNING_KEY, "из prefs").apply()

        val warning = resolveBackendWarning(
            warningMessage = null,
            prefs = prefs
        )

        assertEquals("из prefs", warning)
        assertNull(prefs.getString(BACKEND_AUTH_WARNING_KEY, null))
    }

    @Test
    fun `createBackendWarningPreferenceListener emits warning on backend key change`() {
        val prefs = InMemorySharedPreferences()
        val emitted = AtomicReference<String?>(null)

        val listener = createBackendWarningPreferenceListener(
            prefs = prefs,
            onWarning = { emitted.set(it) }
        )

        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString(BACKEND_AUTH_WARNING_KEY, "поздняя запись").apply()

        assertEquals("поздняя запись", emitted.get())
        assertNull(prefs.getString(BACKEND_AUTH_WARNING_KEY, null))
    }

    @Test
    fun `createBackendWarningPreferenceListener ignores unrelated key changes`() {
        val prefs = InMemorySharedPreferences()
        val emitted = AtomicReference<String?>(null)

        val listener = createBackendWarningPreferenceListener(
            prefs = prefs,
            onWarning = { emitted.set(it) }
        )

        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("other_key", "123").apply()

        assertNull(emitted.get())
    }

    @Test
    fun `unregistered listener no longer receives backend warning`() {
        val prefs = InMemorySharedPreferences()
        val emitted = AtomicReference<String?>(null)

        val listener = createBackendWarningPreferenceListener(
            prefs = prefs,
            onWarning = { emitted.set(it) }
        )

        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString(BACKEND_AUTH_WARNING_KEY, "не должно прилететь").apply()

        assertNull(emitted.get())
        assertEquals("не должно прилететь", prefs.getString(BACKEND_AUTH_WARNING_KEY, null))
    }

    @Test
    fun `module actions are hidden when both optional features are disabled`() {
        val enabledFlags = emptySet<FeatureFlag>()

        assertFalse(shouldShowEgaisAction(enabledFlags))
        assertFalse(shouldShowChaseznakAction(enabledFlags))
    }

    @Test
    fun `egais action is visible only when EGAAIS feature is enabled`() {
        val enabledFlags = setOf(FeatureFlag.EGAAIS_ENABLED)

        assertTrue(shouldShowEgaisAction(enabledFlags))
        assertFalse(shouldShowChaseznakAction(enabledFlags))
    }

    @Test
    fun `chaseznak action is visible only when CHASEZNAK feature is enabled`() {
        val enabledFlags = setOf(FeatureFlag.CHASEZNAK_ENABLED)

        assertFalse(shouldShowEgaisAction(enabledFlags))
        assertTrue(shouldShowChaseznakAction(enabledFlags))
    }

    @Test
    fun `both module actions are visible when both optional features are enabled`() {
        val enabledFlags = setOf(FeatureFlag.EGAAIS_ENABLED, FeatureFlag.CHASEZNAK_ENABLED)

        assertTrue(shouldShowEgaisAction(enabledFlags))
        assertTrue(shouldShowChaseznakAction(enabledFlags))
    }

}

private class InMemorySharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

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

    override fun edit(): SharedPreferences.Editor = Editor(data, listeners)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.remove(listener)
    }

    private class Editor(
        private val data: MutableMap<String, Any?>,
        private val listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>
    ) : SharedPreferences.Editor {
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
            val changedKeys = mutableListOf<String>()
            pending.forEach { (key, value) ->
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
                changedKeys.add(key)
            }
            pending.clear()
            changedKeys.forEach { key ->
                listeners.toList().forEach { listener ->
                    listener.onSharedPreferenceChanged(null, key)
                }
            }
        }
    }
}
