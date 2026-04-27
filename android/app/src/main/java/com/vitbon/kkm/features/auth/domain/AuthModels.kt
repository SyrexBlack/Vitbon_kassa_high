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
    data class Success(
        val cashier: AuthenticatedCashier,
        val backendWarning: String? = null
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}

enum class RoleOperation {
    SALE,
    RETURN,
    SHIFT_OPEN,
    SHIFT_CLOSE,
    SHIFT_X_REPORT,
    CASH_IN,
    CASH_OUT
}

object RolePolicy {
    const val ACCESS_DENIED_MESSAGE = "Операция запрещена для текущей роли"

    fun canPerform(role: CashierRole?, operation: RoleOperation): Boolean {
        val currentRole = role ?: return false
        return when (operation) {
            RoleOperation.SALE,
            RoleOperation.RETURN -> true
            RoleOperation.SHIFT_OPEN,
            RoleOperation.SHIFT_CLOSE,
            RoleOperation.SHIFT_X_REPORT,
            RoleOperation.CASH_IN,
            RoleOperation.CASH_OUT -> currentRole == CashierRole.ADMIN || currentRole == CashierRole.SENIOR_CASHIER
        }
    }
}
