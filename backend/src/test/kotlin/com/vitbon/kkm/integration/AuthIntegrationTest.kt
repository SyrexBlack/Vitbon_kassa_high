package com.vitbon.kkm.integration

import com.vitbon.kkm.api.dto.CashierDto
import com.vitbon.kkm.api.dto.LoginFeaturesDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.api.dto.LoginResponseDto
import com.vitbon.kkm.domain.service.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    lateinit var authService: AuthService

    @Test
    fun `LoginRequestDto - includes deviceId`() {
        val req = LoginRequestDto(pin = "1234", deviceId = "DEVICE-1")
        assertEquals("1234", req.pin)
        assertEquals("DEVICE-1", req.deviceId)
    }

    @Test
    fun `LoginResponseDto - contains session expiry`() {
        val resp = LoginResponseDto(
            token = "opaque-token-here",
            cashier = CashierDto(id = "c1", name = "Иванов И.И.", role = "CASHIER"),
            features = LoginFeaturesDto(
                egaisEnabled = true,
                chaseznakEnabled = false,
                acquiringEnabled = true,
                sbpEnabled = false
            ),
            expiresAt = OffsetDateTime.now().plusHours(8).toInstant().toEpochMilli()
        )
        assertEquals("c1", resp.cashier.id)
        assertEquals("CASHIER", resp.cashier.role)
        assertTrue(resp.features.egaisEnabled)
        assertFalse(resp.features.chaseznakEnabled)
        assertTrue(resp.expiresAt > 0)
    }

    @Test
    fun `CashierDto - all roles represented`() {
        listOf("ADMIN", "SENIOR_CASHIER", "CASHIER").forEach { role ->
            val dto = CashierDto("id", "Test", role)
            assertEquals(role, dto.role)
        }
    }

    @Test
    fun `AuthService login issues unique opaque token and expiry for valid pin`() {
        val first = authService.login("1111", "DEVICE-1")
        val second = authService.login("1111", "DEVICE-2")

        assertTrue(first.token.isNotBlank())
        assertTrue(second.token.isNotBlank())
        assertNotEquals(first.token, second.token)
        assertNotEquals("demo-token-1111", first.token)
        assertNotNull(first.expiresAt)
        assertTrue(first.expiresAt > System.currentTimeMillis())
        assertEquals("cashier-demo-1", first.cashier.id)
        assertEquals("Демо Кассир", first.cashier.name)
        assertEquals("CASHIER", first.cashier.role)
        assertFalse(first.features.egaisEnabled)
        assertFalse(first.features.chaseznakEnabled)
        assertTrue(first.features.acquiringEnabled)
        assertTrue(first.features.sbpEnabled)
    }

    @Test
    fun `AuthService login throws unauthorized for invalid pin`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            authService.login("9999", "DEVICE-1")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }
}
