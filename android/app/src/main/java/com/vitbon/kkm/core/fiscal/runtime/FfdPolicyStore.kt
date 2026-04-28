package com.vitbon.kkm.core.fiscal.runtime

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

data class FfdPolicyState(
    val version: String?,
    val source: String,
    val locked: Boolean,
    val updatedAt: Long
)

@Singleton
class FfdPolicyStore @Inject constructor(
    private val prefs: SharedPreferences
) {
    fun saveResolved(version: String, source: String, locked: Boolean, timestampMs: Long) {
        prefs.edit()
            .putString("ffd.version", version)
            .putString("ffd.source", source)
            .putBoolean("ffd.locked", locked)
            .putLong("ffd.updatedAt", timestampMs)
            .apply()
    }

    fun read(): FfdPolicyState {
        return FfdPolicyState(
            version = prefs.getString("ffd.version", null),
            source = prefs.getString("ffd.source", "unknown") ?: "unknown",
            locked = prefs.getBoolean("ffd.locked", false),
            updatedAt = prefs.getLong("ffd.updatedAt", 0L)
        )
    }
}
