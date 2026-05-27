package com.vitbon.kkm.domain.service.security

import java.util.UUID

data class AuthPrincipal(
    val cashierId: UUID,
    val role: String,
    val deviceId: String,
    val sessionId: UUID
)

object SecurityContextHolder {
    private val currentPrincipal = ThreadLocal<AuthPrincipal?>()

    fun set(principal: AuthPrincipal) {
        currentPrincipal.set(principal)
    }

    fun get(): AuthPrincipal? = currentPrincipal.get()

    fun requirePrincipal(): AuthPrincipal {
        return currentPrincipal.get() ?: error("Authenticated principal is not available")
    }

    fun clear() {
        currentPrincipal.remove()
    }
}
