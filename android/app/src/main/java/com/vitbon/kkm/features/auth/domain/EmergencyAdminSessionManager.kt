package com.vitbon.kkm.features.auth.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyAdminSessionManager @Inject constructor() {
    private var activeAdminId: String? = null
    private var expiresAtMillis: Long = 0L
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
    private var nowOverride: Long? = null

    fun activate(adminId: String) {
        activeAdminId = adminId
        expiresAtMillis = now() + SESSION_TTL_MS
    }

    fun clear() {
        activeAdminId = null
        expiresAtMillis = 0L
    }

    fun isActive(): Boolean {
        val adminId = activeAdminId ?: return false
        if (adminId.isBlank()) return false
        if (now() > expiresAtMillis) {
            clear()
            return false
        }
        return true
    }

    fun canPerform(operation: String): Boolean {
        if (!isActive()) return false
        return operation.uppercase() in ALLOWED_OPERATIONS
    }

    fun setNow(nowMillis: Long) {
        nowOverride = nowMillis
    }

    private fun now(): Long = nowOverride ?: nowProvider()

    private companion object {
        const val SESSION_TTL_MS = 15 * 60 * 1000L
        val ALLOWED_OPERATIONS = setOf("SETTINGS", "DIAGNOSTICS")
    }
}
