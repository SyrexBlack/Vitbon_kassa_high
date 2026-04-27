package com.vitbon.kkm.features.auth.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePolicyTest {

    // ── RETURN ──────────────────────────────────────────────────────────────

    @Test
    fun `RETURN denied for CASHIER role`() {
        assertFalse(
            "RETURN must be denied for CASHIER",
            RolePolicy.canPerform(CashierRole.CASHIER, RoleOperation.RETURN)
        )
    }

    @Test
    fun `RETURN allowed for SENIOR_CASHIER`() {
        assertTrue(
            "RETURN must be allowed for SENIOR_CASHIER",
            RolePolicy.canPerform(CashierRole.SENIOR_CASHIER, RoleOperation.RETURN)
        )
    }

    @Test
    fun `RETURN allowed for ADMIN`() {
        assertTrue(
            "RETURN must be allowed for ADMIN",
            RolePolicy.canPerform(CashierRole.ADMIN, RoleOperation.RETURN)
        )
    }

    @Test
    fun `RETURN denied for null role`() {
        assertFalse(
            "RETURN must be denied when not authenticated",
            RolePolicy.canPerform(null, RoleOperation.RETURN)
        )
    }

    // ── SALE ────────────────────────────────────────────────────────────────

    @Test
    fun `SALE allowed for all authenticated roles`() {
        assertTrue(RolePolicy.canPerform(CashierRole.CASHIER, RoleOperation.SALE))
        assertTrue(RolePolicy.canPerform(CashierRole.SENIOR_CASHIER, RoleOperation.SALE))
        assertTrue(RolePolicy.canPerform(CashierRole.ADMIN, RoleOperation.SALE))
    }

    @Test
    fun `SALE denied for null role`() {
        assertFalse(
            "SALE must be denied when not authenticated",
            RolePolicy.canPerform(null, RoleOperation.SALE)
        )
    }

    // ── SHIFT_CLOSE ─────────────────────────────────────────────────────────

    @Test
    fun `SHIFT_CLOSE allowed for ADMIN`() {
        assertTrue(RolePolicy.canPerform(CashierRole.ADMIN, RoleOperation.SHIFT_CLOSE))
    }

    @Test
    fun `SHIFT_CLOSE allowed for SENIOR_CASHIER`() {
        assertTrue(RolePolicy.canPerform(CashierRole.SENIOR_CASHIER, RoleOperation.SHIFT_CLOSE))
    }

    @Test
    fun `SHIFT_CLOSE denied for CASHIER`() {
        assertFalse(
            "SHIFT_CLOSE must be denied for CASHIER",
            RolePolicy.canPerform(CashierRole.CASHIER, RoleOperation.SHIFT_CLOSE)
        )
    }

    @Test
    fun `SHIFT_CLOSE denied for null role`() {
        assertFalse(
            "SHIFT_CLOSE must be denied when not authenticated",
            RolePolicy.canPerform(null, RoleOperation.SHIFT_CLOSE)
        )
    }

    // ── CASH_IN / CASH_OUT ─────────────────────────────────────────────────

    @Test
    fun `CASH_IN allowed for SENIOR_CASHIER and ADMIN`() {
        assertTrue(RolePolicy.canPerform(CashierRole.SENIOR_CASHIER, RoleOperation.CASH_IN))
        assertTrue(RolePolicy.canPerform(CashierRole.ADMIN, RoleOperation.CASH_IN))
        assertFalse(RolePolicy.canPerform(CashierRole.CASHIER, RoleOperation.CASH_IN))
    }

    @Test
    fun `CASH_OUT allowed for SENIOR_CASHIER and ADMIN`() {
        assertTrue(RolePolicy.canPerform(CashierRole.SENIOR_CASHIER, RoleOperation.CASH_OUT))
        assertTrue(RolePolicy.canPerform(CashierRole.ADMIN, RoleOperation.CASH_OUT))
        assertFalse(RolePolicy.canPerform(CashierRole.CASHIER, RoleOperation.CASH_OUT))
    }

    // ── null role blanket deny ──────────────────────────────────────────────

    @Test
    fun `null role denied for all operations`() {
        for (op in RoleOperation.entries) {
            assertFalse("null must be denied for $op", RolePolicy.canPerform(null, op))
        }
    }
}
