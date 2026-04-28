package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuditEventRepository
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRouteGuardIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var auditEventRepository: AuditEventRepository

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @BeforeEach
    fun setUpCashierFixture() {
        authSessionRepository.deleteAll()
        auditEventRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Демо Кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = "CASHIER",
                createdAt = OffsetDateTime.now()
            )
        )
    }

    @Test
    fun `checks endpoint returns 401 without bearer token`() {
        mockMvc.perform(get("/api/v1/checks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `checks endpoint writes audit event for missing bearer token`() {
        mockMvc.perform(get("/api/v1/checks"))
            .andExpect(status().isUnauthorized)

        val events = auditEventRepository.findAll()
        val deny = events.lastOrNull { it.action == "security.auth_deny" }
        assertNotNull(deny)
        val audited = deny!!
        assertEquals("DENY", audited.result)
        assertEquals("MISSING_BEARER", audited.reason)
        assertEquals("/api/v1/checks", audited.target)
    }

    @Test
    fun `statuses endpoint returns 403 for cashier token`() {
        val token = loginAndGetToken(deviceId = "DEVICE-RBAC-1")
        upsertDemoCashierRole("CASHIER")

        mockMvc.perform(
            get("/api/v1/statuses")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-RBAC-1")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `statuses endpoint returns 200 when demo cashier role is elevated to admin`() {
        val token = loginAndGetToken(deviceId = "DEVICE-RBAC-ELEVATION")
        upsertDemoCashierRole("ADMIN")

        mockMvc.perform(
            get("/api/v1/statuses")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-RBAC-ELEVATION")
        ).andExpect(status().isOk)
    }

    @Test
    fun `unknown protected route returns 403 with valid token by default-deny policy`() {
        val token = loginAndGetToken(deviceId = "DEVICE-UNKNOWN-ROUTE")

        mockMvc.perform(
            get("/api/v1/unknown-protected")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-UNKNOWN-ROUTE")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `logout endpoint returns 200 with valid bearer token`() {
        val token = loginAndGetToken(deviceId = "DEVICE-LOGOUT-OK")

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-LOGOUT-OK")
        ).andExpect(status().isOk)
    }

    @Test
    fun `authorized request writes route access audit event`() {
        val token = loginAndGetToken(deviceId = "DEVICE-ACCESS-AUDIT")

        mockMvc.perform(
            get("/api/v1/checks")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-ACCESS-AUDIT")
        ).andExpect(status().isOk)

        val access = auditEventRepository.findAll()
            .lastOrNull { it.action == "security.route_access" && it.result == "ALLOW" }
        assertNotNull(access)
        val audited = access!!
        assertEquals("/api/v1/checks", audited.target)
        assertEquals("CASHIER", audited.actorRole)
        assertEquals("DEVICE-ACCESS-AUDIT", audited.deviceId)
        assertNotNull(audited.sessionId)
    }

    @Test
    fun `protected route denies token replay from different device`() {
        val token = loginAndGetToken(deviceId = "DEVICE-A")

        mockMvc.perform(
            get("/api/v1/checks")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-B")
        ).andExpect(status().isUnauthorized)

        val deny = auditEventRepository.findAll()
            .lastOrNull { it.action == "security.auth_deny" && it.reason == "DEVICE_MISMATCH" }
        assertNotNull(deny)
        val audited = deny!!
        assertEquals("/api/v1/checks", audited.target)
        assertEquals("DEVICE-B", audited.deviceId)
    }

    private fun loginAndGetToken(deviceId: String): String {
        val loginBody = LoginRequestDto(pin = "1234", deviceId = deviceId)
        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
            .andReturn()

        val node = objectMapper.readTree(loginResponse.response.contentAsString)
        return node.get("token").asText()
    }

    private fun upsertDemoCashierRole(role: String) {
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Демо Кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = role,
                createdAt = OffsetDateTime.now()
            )
        )
    }
}
