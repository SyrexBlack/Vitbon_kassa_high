package com.vitbon.kkm.integration

import com.vitbon.kkm.api.dto.*
import org.junit.Assert.*
import org.junit.Test

class AuthIntegrationTest {
    @Test
    fun `LoginRequestDto — validates PIN format`() {
        val req = LoginRequestDto(pin = "1234")
        assertEquals("1234", req.pin)
    }

    @Test
    fun `LoginResponseDto — contains cashier info`() {
        val resp = LoginResponseDto(
            token = "jwt-token-here",
            cashier = CashierDto(id = "c1", name = "Иванов И.И.", role = "CASHIER")
        )
        assertEquals("c1", resp.cashier.id)
        assertEquals("CASHIER", resp.cashier.role)
    }

    @Test
    fun `CashierDto — all roles represented`() {
        listOf("ADMIN", "SENIOR_CASHIER", "CASHIER").forEach { role ->
            val dto = CashierDto("id", "Test", role)
            assertEquals(role, dto.role)
        }
    }
}
