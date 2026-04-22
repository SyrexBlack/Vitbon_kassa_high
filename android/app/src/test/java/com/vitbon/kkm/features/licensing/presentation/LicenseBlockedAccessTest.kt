package com.vitbon.kkm.features.licensing.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseBlockedAccessTest {

    @Test
    fun `reports route is allowed in blocked mode`() {
        assertTrue(isRouteAllowedWhenBlocked("reports"))
    }

    @Test
    fun `statuses route is allowed in blocked mode`() {
        assertTrue(isRouteAllowedWhenBlocked("statuses"))
    }

    @Test
    fun `sales route is blocked in blocked mode`() {
        assertFalse(isRouteAllowedWhenBlocked("sales/cashier/name/null"))
    }

    @Test
    fun `return route is blocked in blocked mode`() {
        assertFalse(isRouteAllowedWhenBlocked("return"))
    }

    @Test
    fun `correction route is blocked in blocked mode`() {
        assertFalse(isRouteAllowedWhenBlocked("correction"))
    }

    @Test
    fun `cash drawer route is blocked in blocked mode`() {
        assertFalse(isRouteAllowedWhenBlocked("cash_drawer"))
    }

    @Test
    fun `egais and chaseznak routes are blocked in blocked mode`() {
        assertFalse(isRouteAllowedWhenBlocked("egais"))
        assertFalse(isRouteAllowedWhenBlocked("chaseznak"))
    }

    @Test
    fun `nested reports and statuses routes stay allowed`() {
        assertTrue(isRouteAllowedWhenBlocked("reports/detail"))
        assertTrue(isRouteAllowedWhenBlocked("statuses/detail"))
    }

    @Test
    fun `prefix collisions are blocked`() {
        assertFalse(isRouteAllowedWhenBlocked("reports_export"))
        assertFalse(isRouteAllowedWhenBlocked("statuses_extra"))
    }
}
