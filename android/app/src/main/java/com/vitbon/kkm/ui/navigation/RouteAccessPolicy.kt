package com.vitbon.kkm.ui.navigation

import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.auth.domain.RoleOperation
import com.vitbon.kkm.features.auth.domain.RolePolicy

fun canAccessRoute(role: CashierRole?, route: String): Boolean {
    val baseRoute = route.substringBefore("/")
    return when (baseRoute) {
        NavRoutes.AUTH -> true
        NavRoutes.SALES.substringBefore("/") -> role != null
        NavRoutes.RETURN -> RolePolicy.canPerform(role, RoleOperation.RETURN)
        NavRoutes.SHIFT -> RolePolicy.canPerform(role, RoleOperation.SHIFT_OPEN)
        NavRoutes.CASH_DRAWER -> RolePolicy.canPerform(role, RoleOperation.CASH_IN)
        NavRoutes.CORRECTION -> RolePolicy.canPerform(role, RoleOperation.CORRECTION)
        NavRoutes.REPORTS,
        NavRoutes.STATUSES,
        NavRoutes.EGAIS,
        NavRoutes.CHASEZNAK -> role != null
        else -> false
    }
}
