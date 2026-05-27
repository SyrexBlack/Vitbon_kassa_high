package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.ShiftEntity
import com.vitbon.kkm.domain.persistence.ShiftRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
class CheckSyncIdempotencyIntegrationTest {

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

    @Autowired
    lateinit var checkRepository: CheckRepository

    @BeforeEach
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        checkRepository.deleteAll()
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

        shiftRepository.saveAll(
            listOf(
                ShiftEntity(
                    id = UUID.fromString(SHIFT_A_ID),
                    cashierId = UUID.fromString(CASHIER_A_ID),
                    deviceId = "DEVICE-A",
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                ),
                ShiftEntity(
                    id = UUID.fromString(SHIFT_B_ID),
                    cashierId = UUID.fromString(CASHIER_B_ID),
                    deviceId = "DEVICE-B",
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                )
            )
        )
    }

    @Test
    fun `checks sync is idempotent for repeated localUuid`() {
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val request = CheckSyncRequestDto(
            checks = listOf(buildCheck(localUuid = "replay-local-1", shiftId = SHIFT_A_ID))
        )

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.failed").isEmpty)

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.failed").isEmpty)

        assertEquals(1, checkRepository.findAll().size)
    }

    @Test
    fun `checks sync reports per-item failure and still persists valid checks`() {
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val request = CheckSyncRequestDto(
            checks = listOf(
                buildCheck(localUuid = "valid-local-1", shiftId = SHIFT_A_ID),
                buildCheck(localUuid = "invalid-local-1", shiftId = SHIFT_B_ID)
            )
        )

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.failed[0].localUuid").value("invalid-local-1"))

        assertEquals(1, checkRepository.findAll().size)
        assertEquals("valid-local-1", checkRepository.findAll().single().localUuid)
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

    private fun buildCheck(localUuid: String, shiftId: String): CheckDto {
        return CheckDto(
            id = UUID.randomUUID().toString(),
            localUuid = localUuid,
            shiftId = shiftId,
            cashierId = CASHIER_A_ID,
            deviceId = "DEVICE-A",
            type = "SALE",
            fiscalSign = null,
            ffdVersion = "1.05",
            subtotal = 10000L,
            discount = 0L,
            total = 10000L,
            taxAmount = 2000L,
            paymentType = "cash",
            items = listOf(
                CheckItemDto(
                    id = UUID.randomUUID().toString(),
                    productId = null,
                    barcode = "4607001234567",
                    name = "Тестовый товар",
                    quantity = 1.0,
                    price = 10000L,
                    discount = 0L,
                    vatRate = "VAT_20",
                    total = 10000L
                )
            ),
            createdAt = System.currentTimeMillis()
        )
    }

    private fun Long.toOffsetDateTime() = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    private companion object {
        const val CASHIER_A_ID = "11111111-1111-1111-1111-111111111111"
        const val CASHIER_B_ID = "22222222-2222-2222-2222-222222222222"
        const val SHIFT_A_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val SHIFT_B_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    }
}