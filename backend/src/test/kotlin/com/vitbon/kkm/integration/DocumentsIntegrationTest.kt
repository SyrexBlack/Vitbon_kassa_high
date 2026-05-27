package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.api.dto.DocumentItemDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DocumentsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @BeforeEach
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString(CASHIER_ID),
                name = "Старший кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = "SENIOR_CASHIER",
                createdAt = OffsetDateTime.now(ZoneOffset.UTC)
            )
        )
    }

    @Test
    fun `POST documents acceptance accepts valid payload`() {
        val token = loginAndGetToken()

        val doc = DocumentDto(
            type = "ACCEPTANCE",
            items = listOf(
                DocumentItemDto(
                    productId = null,
                    barcode = "4607001234567",
                    name = "Вода 0.5л",
                    quantity = 2.0,
                    reason = null
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents writeoff accepts valid payload with reason`() {
        val token = loginAndGetToken()

        val doc = DocumentDto(
            type = "WRITEOFF",
            items = listOf(
                DocumentItemDto(
                    productId = null,
                    barcode = "4607001234567",
                    name = "Вода 0.5л",
                    quantity = 1.0,
                    reason = "Бой"
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        mockMvc.perform(
            post("/api/v1/documents/writeoff")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents inventory accepts valid payload`() {
        val token = loginAndGetToken()

        val doc = DocumentDto(
            type = "INVENTORY",
            items = listOf(
                DocumentItemDto(
                    productId = null,
                    barcode = "4607001234567",
                    name = "Вода 0.5л",
                    quantity = -2.0,
                    reason = null
                )
            ),
            timestamp = System.currentTimeMillis()
        )

        mockMvc.perform(
            post("/api/v1/documents/inventory")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents acceptance rejects malformed payload`() {
        val token = loginAndGetToken()

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ACCEPTANCE\",\"items\":\"bad\",\"timestamp\":1}")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST documents acceptance rejects empty items`() {
        val token = loginAndGetToken()

        val doc = DocumentDto(
            type = "ACCEPTANCE",
            items = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isBadRequest)
    }

    private fun loginAndGetToken(): String {
        val loginBody = LoginRequestDto(pin = "1234", deviceId = DEVICE_ID)
        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
            .andReturn()

        return objectMapper.readTree(loginResponse.response.contentAsString)
            .get("token")
            .asText()
    }

    private companion object {
        const val CASHIER_ID = "11111111-1111-1111-1111-111111111111"
        const val DEVICE_ID = "TEST-DEVICE"
    }
}
