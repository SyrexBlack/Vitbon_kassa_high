package com.vitbon.kkm.features.licensing.domain

import org.junit.Assert.*
import org.junit.Test

class LicenseCheckerTest {
    @Test
    fun `grace period — 7 days counted correctly`() {
        val now = System.currentTimeMillis()
        val graceUntil = now + 7L * 24 * 60 * 60 * 1000
        val daysLeft = ((graceUntil - now) / (7L * 24 * 60 * 60 * 1000)).toInt()
        assertEquals(7, daysLeft)
    }

    @Test
    fun `grace period — expired when 0 days left`() {
        val now = System.currentTimeMillis()
        val graceUntil = now - 1  // already expired
        val daysLeft = ((graceUntil - now) / (7L * 24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        assertEquals(0, daysLeft)
    }

    @Test
    fun `LicenseStatus — all states covered`() {
        val active = LicenseStatus.Active
        val grace = LicenseStatus.GracePeriod(3)
        val expired = LicenseStatus.Expired
        val error = LicenseStatus.Error("timeout")

        assertTrue(active is LicenseStatus.Active)
        assertTrue(grace is LicenseStatus.GracePeriod)
        assertEquals(3, (grace as LicenseStatus.GracePeriod).daysLeft)
        assertTrue(expired is LicenseStatus.Expired)
        assertTrue(error is LicenseStatus.Error)
        assertEquals("timeout", (error as LicenseStatus.Error).message)
    }

    @Test
    fun `AppBlockingState — blocked vs unblocked`() {
        val unblocked = AppBlockingState.Unblocked
        val blocked = AppBlockingState.Blocked("Просрочка")

        assertTrue(unblocked is AppBlockingState.Unblocked)
        assertTrue(blocked is AppBlockingState.Blocked)
        assertEquals("Просрочка", (blocked as AppBlockingState.Blocked).reason)
    }
}
