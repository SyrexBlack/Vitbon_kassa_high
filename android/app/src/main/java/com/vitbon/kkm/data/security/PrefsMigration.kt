package com.vitbon.kkm.data.security

import android.content.SharedPreferences

object PrefsMigration {
    const val KEY_LICENSE_STATUS = "license_status"
    const val KEY_LAST_CHECK = "last_check_ts"
    const val KEY_GRACE_UNTIL = "grace_until_ts"

    const val KEY_ROOT_CACHED_RESULT = "root_risk_cached"
    const val KEY_ROOT_CACHED_TS = "root_risk_ts"

    const val KEY_DEVICE_ID = "device_id"
    const val KEY_LAST_SYNC_TIMESTAMP = "lastSyncTimestamp"
    const val KEY_LAST_PRODUCT_SYNC_TIMESTAMP = "lastProductSyncTimestamp"

    fun migrateLicenseData(securePrefs: SharedPreferences, plainPrefs: SharedPreferences) {
        migrateString(securePrefs, plainPrefs, KEY_LICENSE_STATUS)
        migrateLong(securePrefs, plainPrefs, KEY_LAST_CHECK)
        migrateLong(securePrefs, plainPrefs, KEY_GRACE_UNTIL)
    }

    fun migrateRootRiskData(securePrefs: SharedPreferences, plainPrefs: SharedPreferences) {
        migrateString(securePrefs, plainPrefs, KEY_ROOT_CACHED_RESULT)
        migrateLong(securePrefs, plainPrefs, KEY_ROOT_CACHED_TS)
    }

    fun migrateSyncData(securePrefs: SharedPreferences, plainPrefs: SharedPreferences) {
        migrateString(securePrefs, plainPrefs, KEY_DEVICE_ID)
        migrateLong(securePrefs, plainPrefs, KEY_LAST_SYNC_TIMESTAMP)
        migrateLong(securePrefs, plainPrefs, KEY_LAST_PRODUCT_SYNC_TIMESTAMP)
    }

    private fun migrateString(
        securePrefs: SharedPreferences,
        plainPrefs: SharedPreferences,
        key: String
    ) {
        if (securePrefs.contains(key) || !plainPrefs.contains(key)) return

        val value = plainPrefs.getString(key, null)
        try {
            securePrefs.edit().putString(key, value).apply()
            plainPrefs.edit().remove(key).apply()
        } catch (_: Exception) {
            plainPrefs.edit().remove(key).apply()
        }
    }

    private fun migrateLong(
        securePrefs: SharedPreferences,
        plainPrefs: SharedPreferences,
        key: String
    ) {
        if (securePrefs.contains(key) || !plainPrefs.contains(key)) return

        val value = plainPrefs.getLong(key, 0L)
        try {
            securePrefs.edit().putLong(key, value).apply()
            plainPrefs.edit().remove(key).apply()
        } catch (_: Exception) {
            plainPrefs.edit().remove(key).apply()
        }
    }
}
