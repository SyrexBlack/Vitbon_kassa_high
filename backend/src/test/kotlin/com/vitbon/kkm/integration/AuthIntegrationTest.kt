package com.vitbon.kkm.integration

import com.vitbon.kkm.api.dto.CashierDto
import com.vitbon.kkm.api.dto.LoginFeaturesDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.api.dto.LoginResponseDto
import com.vitbon.kkm.domain.persistence.AuditEventRepository
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.service.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var auditEventRepository: AuditEventRepository

    @BeforeEach
    fun setUpCashiers() {
        authSessionRepository.deleteAll()
        auditEventRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.saveAll(
            listOf(
                CashierEntity(
                    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    name = "Демо Кассир",
                    pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                    role = "CASHIER",
                    createdAt = OffsetDateTime.now()
                ),
                CashierEntity(
                    id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    name = "Старший Кассир",
                    pinHash = "edee29f882543b956620b26d0ee0e7e950399b1c4222f5de05e06425b4c995e9",
                    role = "SENIOR_CASHIER",
                    createdAt = OffsetDateTime.now()
                ),
                CashierEntity(
                    id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    name = "Администратор",
                    pinHash = "318aee3fed8c9d040d35a7fc1fa776fb31303833aa2de885354ddf3d44d8fb69",
                    role = "ADMIN",
                    createdAt = OffsetDateTime.now()
                )
            )
        )
    }

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
        val first = authService.login("1234", "DEVICE-1")
        val second = authService.login("1234", "DEVICE-2")

        assertTrue(first.token.isNotBlank())
        assertTrue(second.token.isNotBlank())
        assertNotEquals(first.token, second.token)
        assertNotNull(first.expiresAt)
        assertTrue(first.expiresAt > System.currentTimeMillis())
        assertEquals("11111111-1111-1111-1111-111111111111", first.cashier.id)
        assertEquals("Демо Кассир", first.cashier.name)
        assertEquals("CASHIER", first.cashier.role)
        assertFalse(first.features.egaisEnabled)
        assertFalse(first.features.chaseznakEnabled)
        assertTrue(first.features.acquiringEnabled)
        assertTrue(first.features.sbpEnabled)
    }

    @Test
    fun `AuthService login enforces one active session per cashier`() {
        authService.login("1234", "DEVICE-1")
        authService.login("1234", "DEVICE-2")

        val sessions = authSessionRepository.findAllByCashierIdOrderByIssuedAtDesc(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val active = sessions.filter { it.revokedAt == null }
        val revoked = sessions.filter { it.revokedAt != null }

        assertEquals(1, active.size)
        assertEquals(1, revoked.size)
        assertEquals("REPLACED_BY_NEW_LOGIN", revoked.first().revokeReason)
    }

    @Test
    fun `AuthService login resolves role from cashier repository`() {
        val senior = authService.login("2222", "DEVICE-SENIOR")
        val admin = authService.login("3333", "DEVICE-ADMIN")

        assertEquals("SENIOR_CASHIER", senior.cashier.role)
        assertEquals("ADMIN", admin.cashier.role)
    }

    @Test
    fun `AuthService login throws unauthorized for invalid pin`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            authService.login("9999", "DEVICE-1")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    @Test
    fun `AuthService blocks repeated invalid pin attempts per device`() {
        repeat(3) {
            val exception = assertThrows(ResponseStatusException::class.java) {
                authService.login("9999", "DEVICE-BRUTEFORCE")
            }
            assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        }

        val blocked = assertThrows(ResponseStatusException::class.java) {
            authService.login("9999", "DEVICE-BRUTEFORCE")
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.statusCode)

        val hasAudit = auditEventRepository.findAll().any {
            it.action == "auth.login" && it.result == "DENY" && it.reason == "RATE_LIMITED"
        }
        assertTrue(hasAudit)
    }
}
