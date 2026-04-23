package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LoginRequestDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRouteGuardIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `checks endpoint returns 401 without bearer token`() {
        mockMvc.perform(get("/api/v1/checks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `statuses endpoint returns 403 for cashier token`() {
        val token = loginAndGetToken(deviceId = "DEVICE-RBAC-1")
        upsertDemoCashierRole("CASHIER")

        mockMvc.perform(get("/api/v1/statuses").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `statuses endpoint returns 200 when demo cashier role is elevated to admin`() {
        val token = loginAndGetToken(deviceId = "DEVICE-RBAC-ELEVATION")
        upsertDemoCashierRole("ADMIN")

        mockMvc.perform(get("/api/v1/statuses").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    private fun loginAndGetToken(deviceId: String): String {
        val loginBody = LoginRequestDto(pin = "1111", deviceId = deviceId)
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
        withConnection { conn ->
            conn.prepareStatement(
                """
                MERGE INTO cashiers (id, name, pin_hash, role, created_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, UUID.fromString("11111111-1111-1111-1111-111111111111"))
                ps.setString(2, "Демо Кассир")
                ps.setString(3, "demo-pin-hash")
                ps.setString(4, role)
                ps.setTimestamp(5, Timestamp.from(Instant.now()))
                ps.executeUpdate()
            }
        }
    }

    private fun withConnection(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:h2:mem:vitbon;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "").use(block)
    }
}
