package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.api.dto.ShiftDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.persistence.CheckEntity
import com.vitbon.kkm.domain.persistence.CheckItemEntity
import com.vitbon.kkm.domain.persistence.CheckRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class CheckOwnershipIntegrationTest {

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

        createShift(id = SHIFT_A_ID, cashierId = CASHIER_A_ID, deviceId = "DEVICE-A")
        createShift(id = SHIFT_B_ID, cashierId = CASHIER_B_ID, deviceId = "DEVICE-B")
    }

    @Test
    fun `POST check uses authenticated cashier and device instead of request body values`() {
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val check = buildCheck(
            id = "33333333-3333-3333-3333-333333333333",
            localUuid = "check-local-1",
            shiftId = SHIFT_A_ID,
            cashierId = CASHIER_B_ID,
            deviceId = "FORGED-DEVICE"
        )

        mockMvc.perform(
            post("/api/v1/checks")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(check))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cashierId").value(CASHIER_A_ID))
            .andExpect(jsonPath("$.deviceId").value("DEVICE-A"))
    }

    @Test
    fun `POST checks-sync reports foreign shift ownership as item failure`() {
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        val request = CheckSyncRequestDto(
            listOf(
                buildCheck(
                    id = "44444444-4444-4444-4444-444444444444",
                    localUuid = "check-local-2",
                    shiftId = SHIFT_B_ID,
                    cashierId = CASHIER_A_ID,
                    deviceId = "DEVICE-A"
                )
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
            .andExpect(jsonPath("$.processed").value(0))
            .andExpect(jsonPath("$.failed[0].localUuid").value("check-local-2"))
    }

    @Test
    fun `GET check by id hides foreign check`() {
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")
        persistCheck(
            id = "55555555-5555-5555-5555-555555555555",
            localUuid = "check-local-3",
            shiftId = SHIFT_B_ID,
            cashierId = CASHIER_B_ID,
            deviceId = "DEVICE-B",
            total = 8000L,
            createdAt = System.currentTimeMillis()
        )

        mockMvc.perform(
            get("/api/v1/checks/55555555-5555-5555-5555-555555555555")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `GET reports aggregates only authenticated cashier device checks`() {
        val now = System.currentTimeMillis()
        val token = loginAndGetToken(pin = "1234", deviceId = "DEVICE-A")

        persistCheck(
            id = "66666666-6666-6666-6666-666666666666",
            localUuid = "check-local-4",
            shiftId = SHIFT_A_ID,
            cashierId = CASHIER_A_ID,
            deviceId = "DEVICE-A",
            total = 10000L,
            createdAt = now,
            paymentType = "cash",
            itemName = "Вода"
        )
        persistCheck(
            id = "77777777-7777-7777-7777-777777777777",
            localUuid = "check-local-5",
            shiftId = SHIFT_B_ID,
            cashierId = CASHIER_B_ID,
            deviceId = "DEVICE-B",
            total = 9000L,
            createdAt = now,
            paymentType = "card",
            itemName = "Сок"
        )

        mockMvc.perform(
            get("/api/v1/reports")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", "DEVICE-A")
                .param("period", "day")
                .param("since", (now - 1).toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalChecks").value(1))
            .andExpect(jsonPath("$.totalRevenue").value(10000))
            .andExpect(jsonPath("$.cashRevenue").value(10000))
            .andExpect(jsonPath("$.cardRevenue").value(0))
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

    private fun buildCheck(
        id: String,
        localUuid: String,
        shiftId: String,
        cashierId: String,
        deviceId: String,
        total: Long = 10000L,
        createdAt: Long = System.currentTimeMillis(),
        paymentType: String = "cash",
        itemName: String = "Тестовый товар"
    ): CheckDto {
        return CheckDto(
            id = id,
            localUuid = localUuid,
            shiftId = shiftId,
            cashierId = cashierId,
            deviceId = deviceId,
            type = "SALE",
            fiscalSign = null,
            ffdVersion = "1.05",
            subtotal = total,
            discount = 0L,
            total = total,
            taxAmount = 2000L,
            paymentType = paymentType,
            items = listOf(
                CheckItemDto(
                    id = UUID.randomUUID().toString(),
                    productId = null,
                    barcode = "4607001234567",
                    name = itemName,
                    quantity = 1.0,
                    price = total,
                    discount = 0L,
                    vatRate = "VAT_20",
                    total = total
                )
            ),
            createdAt = createdAt
        )
    }

    private fun createShift(id: String, cashierId: String, deviceId: String) {
        val shift = ShiftDto(
            id = id,
            cashierId = cashierId,
            deviceId = deviceId,
            openedAt = System.currentTimeMillis(),
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )

        shiftRepository.save(
            com.vitbon.kkm.domain.persistence.ShiftEntity(
                id = UUID.fromString(shift.id),
                cashierId = UUID.fromString(shift.cashierId),
                deviceId = shift.deviceId,
                openedAt = shift.openedAt.toOffsetDateTime(),
                closedAt = null,
                totalCash = shift.totalCash,
                totalCard = shift.totalCard
            )
        )
    }

    private fun persistCheck(
        id: String,
        localUuid: String,
        shiftId: String,
        cashierId: String,
        deviceId: String,
        total: Long,
        createdAt: Long,
        paymentType: String = "cash",
        itemName: String = "Тестовый товар"
    ) {
        val checkEntity = CheckEntity(
            id = UUID.fromString(id),
            localUuid = localUuid,
            shiftId = UUID.fromString(shiftId),
            cashierId = UUID.fromString(cashierId),
            deviceId = deviceId,
            type = "SALE",
            fiscalSign = null,
            ffdVersion = "1.05",
            subtotal = total,
            discount = 0L,
            total = total,
            taxAmount = 2000L,
            paymentType = paymentType,
            createdAt = createdAt.toOffsetDateTime()
        )
        checkEntity.items.add(
            CheckItemEntity(
                id = UUID.randomUUID(),
                check = checkEntity,
                productId = null,
                barcode = "4607001234567",
                name = itemName,
                quantity = 1.0,
                price = total,
                discount = 0L,
                vatRate = "VAT_20",
                total = total
            )
        )
        checkRepository.save(checkEntity)
    }

    private fun Long.toOffsetDateTime() = OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    private companion object {
        const val CASHIER_A_ID = "11111111-1111-1111-1111-111111111111"
        const val CASHIER_B_ID = "22222222-2222-2222-2222-222222222222"
        const val SHIFT_A_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val SHIFT_B_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    }
}