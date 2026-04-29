package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.SharedPreferences
import android.content.ContextWrapper
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import org.junit.Assert.*
import org.junit.Test

class RootRiskGuardTest {

    @Test
    fun `getCurrentBlockingState returns Unblocked for clean detector`() {
        val prefs = TestSharedPreferences()
        val guard = RootRiskGuard(
            FakeContext(),
            alwaysCleanDetector(),
            prefs
        )
        val state = guard.getCurrentBlockingState()
        assertTrue(state is AppBlockingState.Unblocked)
    }
}

class TestSharedPreferences : SharedPreferences {
    private val storage = mutableMapOf<String, Any?>()
    override fun getAll() = storage.toMap()
    override fun getString(key: String, defValue: String?) = storage[key] as? String ?: defValue
    override fun getLong(key: String, defValue: Long) = storage[key] as? Long ?: defValue
    override fun contains(key: String) = key in storage
    override fun edit(): SharedPreferences.Editor = TestEditor(storage)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun getStringSet(key: String, defValue: MutableSet<String>?) = defValue
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue

    private inner class TestEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { storage[k] = v }
        override fun putLong(k: String, v: Long) = apply { storage[k] = v }
        override fun remove(k: String) = apply { storage.remove(k) }
        override fun clear() = apply { storage.clear() }
        override fun commit() = true
        override fun apply() {}
        override fun putInt(k: String, v: Int) = this
        override fun putBoolean(k: String, v: Boolean) = this
        override fun putFloat(k: String, v: Float) = this
        override fun putStringSet(k: String, v: MutableSet<String>?) = this
    }
}

private class FakeContext : ContextWrapper(android.app.Application())

private fun alwaysCleanDetector() = object : RootDetector {
    override suspend fun check(context: Context): RootCheckResult = RootCheckResult.Clean
}