package com.vitbon.kkm.data.security

import android.content.SharedPreferences
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrefsMigrationTest {

    @Test
    fun `migrateSyncData copies legacy values to secure prefs and removes plain keys`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()

        plainPrefs.edit()
            .putString("device_id", "DEVICE-123")
            .putLong("lastSyncTimestamp", 100L)
            .putLong("lastProductSyncTimestamp", 200L)
            .apply()

        PrefsMigration.migrateSyncData(securePrefs, plainPrefs)

        assertEquals("DEVICE-123", securePrefs.getString("device_id", null))
        assertEquals(100L, securePrefs.getLong("lastSyncTimestamp", 0L))
        assertEquals(200L, securePrefs.getLong("lastProductSyncTimestamp", 0L))
        assertFalse(plainPrefs.contains("device_id"))
        assertFalse(plainPrefs.contains("lastSyncTimestamp"))
        assertFalse(plainPrefs.contains("lastProductSyncTimestamp"))
    }

    @Test
    fun `migrateRootRiskData leaves plain value untouched when secure value already exists`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()

        plainPrefs.edit()
            .putString("root_risk_cached", "DETECTED")
            .putLong("root_risk_ts", 50L)
            .apply()
        securePrefs.edit()
            .putString("root_risk_cached", "CLEAN")
            .putLong("root_risk_ts", 75L)
            .apply()

        PrefsMigration.migrateRootRiskData(securePrefs, plainPrefs)

        assertEquals("CLEAN", securePrefs.getString("root_risk_cached", null))
        assertEquals(75L, securePrefs.getLong("root_risk_ts", 0L))
        assertEquals("DETECTED", plainPrefs.getString("root_risk_cached", null))
        assertEquals(50L, plainPrefs.getLong("root_risk_ts", 0L))
    }

    @Test
    fun `migrateSyncData clears plain values when secure migration fails`() {
        val plainPrefs = InMemorySharedPreferences()
        plainPrefs.edit()
            .putString("device_id", "DEVICE-123")
            .putLong("lastSyncTimestamp", 100L)
            .putLong("lastProductSyncTimestamp", 200L)
            .apply()

        PrefsMigration.migrateSyncData(FailingSharedPreferences(), plainPrefs)

        assertFalse(plainPrefs.contains("device_id"))
        assertFalse(plainPrefs.contains("lastSyncTimestamp"))
        assertFalse(plainPrefs.contains("lastProductSyncTimestamp"))
    }
}

private class FailingSharedPreferences : SharedPreferences {
    override fun getAll(): MutableMap<String, Any?> = mutableMapOf()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = false
    override fun edit(): SharedPreferences.Editor = FailingEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FailingEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = throw IllegalStateException("boom")
        override fun remove(key: String?): SharedPreferences.Editor = this
        override fun clear(): SharedPreferences.Editor = this
        override fun commit(): Boolean = throw IllegalStateException("boom")
        override fun apply() = throw IllegalStateException("boom")
    }
}
