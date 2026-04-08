package com.vitbon.kkm.features.auth.domain

enum class CashierRole {
    ADMIN,
    SENIOR_CASHIER,
    CASHIER
}

data class AuthenticatedCashier(
    val id: String,
    val name: String,
    val role: CashierRole
)

sealed class AuthResult {
    data class Success(val cashier: AuthenticatedCashier) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
