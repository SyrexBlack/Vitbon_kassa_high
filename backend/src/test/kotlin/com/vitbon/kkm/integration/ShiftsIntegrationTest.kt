package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.api.dto.ShiftDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.persistence.ShiftEntity
import com.vitbon.kkm.domain.persistence.ShiftRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ShiftsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @Autowired
    lateinit var shiftRepository: ShiftRepository

    @BeforeEach
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        shiftRepository.deleteAll()
        cashierRepository.deleteAll()

        cashierRepository.saveAll(
            listOf(
                CashierEntity(
                    id = UUID.fromString(CASHIER_A_ID),
                    name = "Кассир А",
                    pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                    role = "CASHIER",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                CashierEntity(
                    id = UUID.fromString(CASHIER_B_ID),
                    name = "Кассир Б",
                    pinHash = "f8638b979b2f4f793ddb6dbd197e0ee25a7a6ea32b0ae22f5e3c5d119d839e75",
                    role = "CASHIER",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
        )
    }

    @Test
    fun `POST shift uses authenticated cashier and device instead of request body values`() {
        val now = System.currentTimeMillis()
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val shiftId = "33333333-3333-3333-3333-333333333333"

        val shift = ShiftDto(
            id = shiftId,
            cashierId = CASHIER_B_ID,
            deviceId = "FORGED-DEVICE",
            openedAt = now,
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )

        mockMvc.perform(
            post("/api/v1/shifts")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shift))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(shiftId))
            .andExpect(jsonPath("$.cashierId").value(CASHIER_A_ID))
            .andExpect(jsonPath("$.deviceId").value("DEVICE-A"))
    }

    @Test
    fun `GET shifts returns only authenticated cashier shifts even when path requests another cashier`() {
        val now = System.currentTimeMillis()
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")

        shiftRepository.save(
            ShiftEntity(
                id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                cashierId = UUID.fromString(CASHIER_A_ID),
                deviceId = "DEVICE-A",
                openedAt = now.toOffsetDateTime(),
                closedAt = null,
                totalCash = 100L,
                totalCard = 200L
            )
        )
        shiftRepository.save(
            ShiftEntity(
                id = UUID.fromString("66666666-6666-6666-6666-666666666666"),
                cashierId = UUID.fromString(CASHIER_B_ID),
                deviceId = "DEVICE-B",
                openedAt = now.toOffsetDateTime(),
                closedAt = null,
                totalCash = 300L,
                totalCard = 400L
            )
        )

        mockMvc.perform(
            get("/api/v1/shifts/$CASHIER_B_ID")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].cashierId").value(CASHIER_A_ID))
            .andExpect(jsonPath("$[0].deviceId").value("DEVICE-A"))
    }

    @Test
    fun `PUT shift close rejects foreign shift ownership`() {
        val now = System.currentTimeMillis()
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val shiftId = "77777777-7777-7777-7777-777777777777"

        shiftRepository.save(
            ShiftEntity(
                id = UUID.fromString(shiftId),
                cashierId = UUID.fromString(CASHIER_B_ID),
                deviceId = "DEVICE-B",
                openedAt = now.toOffsetDateTime(),
                closedAt = null,
                totalCash = 1000L,
                totalCard = 2000L
            )
        )

        mockMvc.perform(
            put("/api/v1/shifts/$shiftId/close")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
        ).andExpect(status().isForbidden)
    }

    private fun loginAndGetToken(pin: String, deviceId: String): String {
        val loginBody = LoginRequestDto(pin = pin, deviceId = deviceId)
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

    private fun Long.toOffsetDateTime() = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    private companion object {
        const val CASHIER_A_ID = "11111111-1111-1111-1111-111111111111"
        const val CASHIER_B_ID = "22222222-2222-2222-2222-222222222222"
    }
}
