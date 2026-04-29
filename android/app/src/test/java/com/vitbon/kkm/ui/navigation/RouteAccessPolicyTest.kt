package com.vitbon.kkm.ui.navigation

import com.vitbon.kkm.features.auth.domain.CashierRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAccessPolicyTest {

    @Test
    fun `cashier cannot access privileged routes`() {
        assertFalse(canAccessRoute(CashierRole.CASHIER, NavRoutes.RETURN))
        assertFalse(canAccessRoute(CashierRole.CASHIER, NavRoutes.CORRECTION))
        assertFalse(canAccessRoute(CashierRole.CASHIER, NavRoutes.CASH_DRAWER))
        assertFalse(canAccessRoute(CashierRole.CASHIER, NavRoutes.SHIFT))
    }

    @Test
    fun `senior cashier can access operational routes`() {
        assertTrue(canAccessRoute(CashierRole.SENIOR_CASHIER, NavRoutes.RETURN))
        assertTrue(canAccessRoute(CashierRole.SENIOR_CASHIER, NavRoutes.CASH_DRAWER))
        assertTrue(canAccessRoute(CashierRole.SENIOR_CASHIER, NavRoutes.SHIFT))
        assertFalse(canAccessRoute(CashierRole.SENIOR_CASHIER, NavRoutes.CORRECTION))
    }

    @Test
    fun `admin can access all privileged routes`() {
        assertTrue(canAccessRoute(CashierRole.ADMIN, NavRoutes.RETURN))
        assertTrue(canAccessRoute(CashierRole.ADMIN, NavRoutes.CORRECTION))
        assertTrue(canAccessRoute(CashierRole.ADMIN, NavRoutes.CASH_DRAWER))
        assertTrue(canAccessRoute(CashierRole.ADMIN, NavRoutes.SHIFT))
    }

    @Test
    fun `unauthenticated user can only access auth route`() {
        assertTrue(canAccessRoute(null, NavRoutes.AUTH))
        assertFalse(canAccessRoute(null, NavRoutes.SALES))
        assertFalse(canAccessRoute(null, NavRoutes.RETURN))
    }
}
