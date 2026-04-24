package com.vitbon.kkm.features.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyAdminSessionManagerTest {

    @Test
    fun `emergency session expires after 15 minutes`() {
        val manager = EmergencyAdminSessionManager()
        manager.setNow(1_000L)
        manager.activate("admin-id")

        assertTrue(manager.isActive())

        manager.setNow(1_000L + 15 * 60 * 1000L + 1)
        assertFalse(manager.isActive())
    }

    @Test
    fun `emergency session denies fiscal operations`() {
        val manager = EmergencyAdminSessionManager()
        manager.setNow(1_000L)
        manager.activate("admin-id")

        assertFalse(manager.canPerform("SALE"))
        assertTrue(manager.canPerform("SETTINGS"))
    }
}
