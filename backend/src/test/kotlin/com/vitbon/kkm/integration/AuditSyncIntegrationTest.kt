package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.AuditSyncEntryDto
import com.vitbon.kkm.api.dto.AuditSyncRequestDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuditEventRepository
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class AuditSyncIntegrationTest {

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
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        auditEventRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString(CASHIER_ID),
                name = "Демо Кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = "CASHIER",
                createdAt = OffsetDateTime.now(ZoneOffset.UTC)
            )
        )
    }

    @Test
    fun `audit sync ingests buffered emergency events into backend audit trail`() {
        val token = loginAndGetToken(deviceId = DEVICE_ID)
        val eventId = "33333333-3333-3333-3333-333333333333"
        val timestamp = 1_710_000_000_000L

        val request = AuditSyncRequestDto(
            events = listOf(
                AuditSyncEntryDto(
                    id = eventId,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    action = "auth.emergency.enter",
                    details = "DENY:BACKEND_AVAILABLE",
                    timestamp = timestamp
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/audit/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.failed").isEmpty)

        val event = auditEventRepository.findById(UUID.fromString(eventId)).orElseThrow()
        assertEquals(UUID.fromString(CASHIER_ID), event.actorId)
        assertEquals("CASHIER", event.actorRole)
        assertEquals(DEVICE_ID, event.deviceId)
        assertNull(event.sessionId)
        assertEquals("auth.emergency.enter", event.action)
        assertEquals("DENY", event.result)
        assertEquals("BACKEND_AVAILABLE", event.reason)
        assertEquals(Instant.ofEpochMilli(timestamp), event.createdAt.toInstant())
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

    companion object {
        private const val CASHIER_ID = "11111111-1111-1111-1111-111111111111"
        private const val DEVICE_ID = "DEVICE-AUDIT-SYNC"
    }
}