package com.vitbon.kkm.core.sync

import com.vitbon.kkm.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncPrefsMigrationTest {

    @Test
    fun `sync prefs migrates legacy values to secure prefs and uses secure store afterward`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()

        plainPrefs.edit()
            .putString("device_id", "legacy-device")
            .putLong("lastSyncTimestamp", 10L)
            .putLong("lastProductSyncTimestamp", 20L)
            .apply()

        val syncPrefs = SyncPrefs(plainPrefs, securePrefs)

        assertEquals("legacy-device", syncPrefs.deviceId)
        assertEquals(10L, syncPrefs.lastSyncTimestamp)
        assertEquals(20L, syncPrefs.lastProductSyncTimestamp)
        assertFalse(plainPrefs.contains("device_id"))
        assertFalse(plainPrefs.contains("lastSyncTimestamp"))
        assertFalse(plainPrefs.contains("lastProductSyncTimestamp"))

        syncPrefs.lastSyncTimestamp = 30L
        syncPrefs.lastProductSyncTimestamp = 40L
        syncPrefs.deviceId = "secure-device"

        assertEquals(30L, securePrefs.getLong("lastSyncTimestamp", 0L))
        assertEquals(40L, securePrefs.getLong("lastProductSyncTimestamp", 0L))
        assertEquals("secure-device", securePrefs.getString("device_id", null))
        assertEquals(0L, plainPrefs.getLong("lastSyncTimestamp", 0L))
        assertEquals(0L, plainPrefs.getLong("lastProductSyncTimestamp", 0L))
        assertEquals(null, plainPrefs.getString("device_id", null))
    }

    @Test
    fun `sync prefs initializes device id from fallback provider when stores are empty`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()

        val syncPrefs = SyncPrefs(plainPrefs, securePrefs) { "ANDROID-ID-123" }

        assertEquals("ANDROID-ID-123", syncPrefs.deviceId)
        assertEquals("ANDROID-ID-123", securePrefs.getString("device_id", null))
        assertFalse(plainPrefs.contains("device_id"))
    }
}
