package com.vitbon.kkm.domain.service.security

import org.springframework.stereotype.Component

@Component
class RouteAccessPolicy {
    fun requiredRoles(method: String, path: String): Set<String> {
        val normalized = path.substringBefore('?')

        if (normalized == "/api/v1/auth/login") return emptySet()

        return when {
            normalized == "/api/v1/license/check" -> emptySet()
            normalized.startsWith("/api/v1/statuses") -> setOf("ADMIN", "SENIOR_CASHIER")
            normalized.startsWith("/api/v1/checks") -> setOf("CASHIER", "SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/shifts") -> setOf("CASHIER", "SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/reports") -> setOf("CASHIER", "SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/products") -> setOf("CASHIER", "SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/documents") -> setOf("SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/egais") -> setOf("SENIOR_CASHIER", "ADMIN")
            normalized.startsWith("/api/v1/chaseznak") -> setOf("CASHIER", "SENIOR_CASHIER", "ADMIN")
            else -> setOf("__DENY_ALL__")
        }
    }
}
