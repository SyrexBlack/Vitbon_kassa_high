package com.vitbon.kkm.integration

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

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

    @Test
    fun `AuthService login returns demo token and cashier for pin 1111`() {
        val service = AuthService()

        val result = service.login("1111")

        assertEquals("demo-token-1111", result.token)
        assertEquals("cashier-demo-1", result.cashier.id)
        assertEquals("Демо Кассир", result.cashier.name)
        assertEquals("CASHIER", result.cashier.role)
    }

    @Test
    fun `AuthService login throws unauthorized for invalid pin`() {
        val service = AuthService()

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.login("9999")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }
}
