package com.vitbon.kkm.core.sync

import android.content.SharedPreferences
import com.vitbon.kkm.data.security.PrefsMigration

class SyncPrefs(
    plainPrefs: SharedPreferences,
    private val securePrefs: SharedPreferences,
    deviceIdProvider: () -> String? = { null }
) {
    init {
        PrefsMigration.migrateSyncData(securePrefs, plainPrefs)
        if (!securePrefs.contains(PrefsMigration.KEY_DEVICE_ID)) {
            deviceIdProvider()?.trim()?.takeIf { it.isNotEmpty() }?.let { deviceId = it }
        }
    }

    var deviceId: String?
        get() = securePrefs.getString(PrefsMigration.KEY_DEVICE_ID, null)
        set(value) {
            securePrefs.edit().putString(PrefsMigration.KEY_DEVICE_ID, value).apply()
        }

    var lastSyncTimestamp: Long
        get() = securePrefs.getLong(PrefsMigration.KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(v) { securePrefs.edit().putLong(PrefsMigration.KEY_LAST_SYNC_TIMESTAMP, v).apply() }

    var lastProductSyncTimestamp: Long
        get() = securePrefs.getLong(PrefsMigration.KEY_LAST_PRODUCT_SYNC_TIMESTAMP, 0L)
        set(v) { securePrefs.edit().putLong(PrefsMigration.KEY_LAST_PRODUCT_SYNC_TIMESTAMP, v).apply() }
}
