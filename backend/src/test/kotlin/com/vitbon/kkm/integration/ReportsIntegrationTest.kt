package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.api.dto.DocumentItemDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.DocumentRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ReportsIntegrationTest {

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

    @Autowired
    lateinit var documentRepository: DocumentRepository

    @BeforeEach
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        checkRepository.deleteAll()
        documentRepository.deleteAll()
        shiftRepository.deleteAll()
        cashierRepository.deleteAll()

        cashierRepository.saveAll(
            listOf(
                CashierEntity(
                    id = UUID.fromString(CASHIER_ID),
                    name = "Старший кассир",
                    pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                    role = "SENIOR_CASHIER",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                CashierEntity(
                    id = UUID.fromString(FOREIGN_CASHIER_ID),
                    name = "Чужой старший кассир",
                    pinHash = "f8638b979b2f4f793ddb6dbd197e0ee25a7a6ea32b0ae22f5e3c5d119d839e75",
                    role = "SENIOR_CASHIER",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
        )

        shiftRepository.saveAll(
            listOf(
                ShiftEntity(
                    id = UUID.fromString(SHIFT_1_ID),
                    cashierId = UUID.fromString(CASHIER_ID),
                    deviceId = DEVICE_ID,
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                ),
                ShiftEntity(
                    id = UUID.fromString(SHIFT_2_ID),
                    cashierId = UUID.fromString(CASHIER_ID),
                    deviceId = DEVICE_ID,
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                ),
                ShiftEntity(
                    id = UUID.fromString(SHIFT_M_ID),
                    cashierId = UUID.fromString(CASHIER_ID),
                    deviceId = DEVICE_ID,
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                ),
                ShiftEntity(
                    id = UUID.fromString(FOREIGN_SHIFT_ID),
                    cashierId = UUID.fromString(FOREIGN_CASHIER_ID),
                    deviceId = FOREIGN_DEVICE_ID,
                    openedAt = System.currentTimeMillis().toOffsetDateTime(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                )
            )
        )
    }

    @Test
    fun `GET reports returns aggregated totals for synced sale checks`() {
        val token = loginAndGetToken()
        val now = System.currentTimeMillis()

        val syncRequest = CheckSyncRequestDto(
            checks = listOf(
                CheckDto(
                    id = "r-check-1",
                    localUuid = "r-local-1",
                    shiftId = SHIFT_1_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
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
                            id = "ri-1",
                            productId = null,
                            barcode = "4607001234567",
                            name = "Вода",
                            quantity = 1.0,
                            price = 10000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 10000L
                        )
                    ),
                    createdAt = now
                ),
                CheckDto(
                    id = "r-check-2",
                    localUuid = "r-local-2",
                    shiftId = SHIFT_1_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    type = "SALE",
                    fiscalSign = null,
                    ffdVersion = "1.05",
                    subtotal = 5000L,
                    discount = 0L,
                    total = 5000L,
                    taxAmount = 1000L,
                    paymentType = "card",
                    items = listOf(
                        CheckItemDto(
                            id = "ri-2",
                            productId = null,
                            barcode = "4607001234568",
                            name = "Сок",
                            quantity = 1.0,
                            price = 5000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 5000L
                        )
                    ),
                    createdAt = now
                ),
                CheckDto(
                    id = "r-check-4",
                    localUuid = "r-local-4",
                    shiftId = SHIFT_1_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    type = "RETURN",
                    fiscalSign = null,
                    ffdVersion = "1.05",
                    subtotal = 2000L,
                    discount = 0L,
                    total = 2000L,
                    taxAmount = 400L,
                    paymentType = "card",
                    items = listOf(
                        CheckItemDto(
                            id = "ri-4",
                            productId = null,
                            barcode = "4607001234568",
                            name = "Сок",
                            quantity = 1.0,
                            price = 2000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 2000L
                        )
                    ),
                    createdAt = now
                ),
                CheckDto(
                    id = "r-check-3",
                    localUuid = "r-local-3",
                    shiftId = SHIFT_2_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    type = "SALE",
                    fiscalSign = null,
                    ffdVersion = "1.05",
                    subtotal = 7000L,
                    discount = 0L,
                    total = 7000L,
                    taxAmount = 1400L,
                    paymentType = "cash",
                    items = listOf(
                        CheckItemDto(
                            id = "ri-3",
                            productId = null,
                            barcode = "4607001234569",
                            name = "Лимонад",
                            quantity = 1.0,
                            price = 7000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 7000L
                        )
                    ),
                    createdAt = now
                )
            )
        )

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(syncRequest))
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/reports")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .param("period", "day")
                .param("shiftId", SHIFT_1_ID)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalChecks").value(2))
            .andExpect(jsonPath("$.returnChecks").value(1))
            .andExpect(jsonPath("$.totalRevenue").value(15000))
            .andExpect(jsonPath("$.totalReturns").value(2000))
            .andExpect(jsonPath("$.cashRevenue").value(10000))
            .andExpect(jsonPath("$.cardRevenue").value(5000))
            .andExpect(jsonPath("$.averageCheck").value(7500))
            .andExpect(jsonPath("$.topProducts[0].name").value("Вода"))
            .andExpect(jsonPath("$.topProducts[0].quantity").value(1.0))
            .andExpect(jsonPath("$.topProducts[0].total").value(10000))
            .andExpect(jsonPath("$.topProducts[1].name").value("Сок"))
            .andExpect(jsonPath("$.topProducts[1].quantity").value(1.0))
            .andExpect(jsonPath("$.topProducts[1].total").value(5000))

        mockMvc.perform(
            get("/api/v1/reports/sales")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .param("period", "day")
                .param("shiftId", SHIFT_1_ID)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalChecks").value(2))
            .andExpect(jsonPath("$.returnChecks").value(1))
            .andExpect(jsonPath("$.totalRevenue").value(15000))
            .andExpect(jsonPath("$.totalReturns").value(2000))
    }

    @Test
    fun `GET movement report returns stock flow totals and item details`() {
        val token = loginAndGetToken()
        val since = System.currentTimeMillis()
        val now = since + 1_000

        val acceptanceDoc = DocumentDto(
            type = "ACCEPTANCE",
            items = listOf(
                DocumentItemDto(productId = null, barcode = "1001", name = "Товар X", quantity = 10.0, reason = null),
                DocumentItemDto(productId = null, barcode = "1002", name = "Товар Y", quantity = 5.0, reason = null)
            ),
            timestamp = now
        )
        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptanceDoc))
        ).andExpect(status().isOk)

        val writeoffDoc = DocumentDto(
            type = "WRITEOFF",
            items = listOf(
                DocumentItemDto(productId = null, barcode = "1002", name = "Товар Y", quantity = 2.0, reason = "Бой")
            ),
            timestamp = now + 1
        )
        mockMvc.perform(
            post("/api/v1/documents/writeoff")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(writeoffDoc))
        ).andExpect(status().isOk)

        val checks = CheckSyncRequestDto(
            checks = listOf(
                CheckDto(
                    id = "m-sale-1",
                    localUuid = "m-sale-local-1",
                    shiftId = SHIFT_M_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    type = "SALE",
                    fiscalSign = null,
                    ffdVersion = "1.05",
                    subtotal = 3000L,
                    discount = 0L,
                    total = 3000L,
                    taxAmount = 500L,
                    paymentType = "cash",
                    items = listOf(
                        CheckItemDto(
                            id = "m-item-sale-1",
                            productId = null,
                            barcode = "1001",
                            name = "Товар X",
                            quantity = 3.0,
                            price = 1000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 3000L
                        )
                    ),
                    createdAt = now + 2
                ),
                CheckDto(
                    id = "m-return-1",
                    localUuid = "m-return-local-1",
                    shiftId = SHIFT_M_ID,
                    cashierId = CASHIER_ID,
                    deviceId = DEVICE_ID,
                    type = "RETURN",
                    fiscalSign = null,
                    ffdVersion = "1.05",
                    subtotal = 1000L,
                    discount = 0L,
                    total = 1000L,
                    taxAmount = 100L,
                    paymentType = "card",
                    items = listOf(
                        CheckItemDto(
                            id = "m-item-return-1",
                            productId = null,
                            barcode = "1001",
                            name = "Товар X",
                            quantity = 1.0,
                            price = 1000L,
                            discount = 0L,
                            vatRate = "VAT_20",
                            total = 1000L
                        )
                    ),
                    createdAt = now + 3
                )
            )
        )
        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checks))
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/reports/movement")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .param("period", "day")
                .param("since", since.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openingStock").value(0.0))
            .andExpect(jsonPath("$.income").value(15.0))
            .andExpect(jsonPath("$.sales").value(3.0))
            .andExpect(jsonPath("$.returns").value(1.0))
            .andExpect(jsonPath("$.writeoff").value(2.0))
            .andExpect(jsonPath("$.closingStock").value(11.0))
            .andExpect(jsonPath("$.items[0].name").value("Товар X"))
            .andExpect(jsonPath("$.items[0].income").value(10.0))
            .andExpect(jsonPath("$.items[0].sales").value(3.0))
            .andExpect(jsonPath("$.items[0].balance").value(8.0))
            .andExpect(jsonPath("$.items[1].name").value("Товар Y"))
            .andExpect(jsonPath("$.items[1].income").value(5.0))
            .andExpect(jsonPath("$.items[1].sales").value(0.0))
            .andExpect(jsonPath("$.items[1].balance").value(3.0))
    }

    @Test
    fun `GET movement report includes opening stock from history before since`() {
        val token = loginAndGetToken()
        val since = System.currentTimeMillis()
        val beforeSince = since - 5_000
        val now = since + 1_000

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DocumentDto(
                            type = "ACCEPTANCE",
                            items = listOf(
                                DocumentItemDto(productId = null, barcode = "1001", name = "Товар X", quantity = 4.0, reason = null),
                                DocumentItemDto(productId = null, barcode = "1002", name = "Товар Y", quantity = 6.0, reason = null)
                            ),
                            timestamp = beforeSince
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DocumentDto(
                            type = "ACCEPTANCE",
                            items = listOf(
                                DocumentItemDto(productId = null, barcode = "1001", name = "Товар X", quantity = 10.0, reason = null),
                                DocumentItemDto(productId = null, barcode = "1002", name = "Товар Y", quantity = 5.0, reason = null)
                            ),
                            timestamp = now
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/documents/writeoff")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DocumentDto(
                            type = "WRITEOFF",
                            items = listOf(
                                DocumentItemDto(productId = null, barcode = "1002", name = "Товар Y", quantity = 2.0, reason = "Бой")
                            ),
                            timestamp = now + 1
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CheckSyncRequestDto(
                            checks = listOf(
                                CheckDto(
                                    id = "m-history-sale-1",
                                    localUuid = "m-history-sale-local-1",
                                    shiftId = SHIFT_M_ID,
                                    cashierId = CASHIER_ID,
                                    deviceId = DEVICE_ID,
                                    type = "SALE",
                                    fiscalSign = null,
                                    ffdVersion = "1.05",
                                    subtotal = 3000L,
                                    discount = 0L,
                                    total = 3000L,
                                    taxAmount = 500L,
                                    paymentType = "cash",
                                    items = listOf(
                                        CheckItemDto(
                                            id = "m-history-item-sale-1",
                                            productId = null,
                                            barcode = "1001",
                                            name = "Товар X",
                                            quantity = 3.0,
                                            price = 1000L,
                                            discount = 0L,
                                            vatRate = "VAT_20",
                                            total = 3000L
                                        )
                                    ),
                                    createdAt = now + 2
                                ),
                                CheckDto(
                                    id = "m-history-return-1",
                                    localUuid = "m-history-return-local-1",
                                    shiftId = SHIFT_M_ID,
                                    cashierId = CASHIER_ID,
                                    deviceId = DEVICE_ID,
                                    type = "RETURN",
                                    fiscalSign = null,
                                    ffdVersion = "1.05",
                                    subtotal = 1000L,
                                    discount = 0L,
                                    total = 1000L,
                                    taxAmount = 100L,
                                    paymentType = "card",
                                    items = listOf(
                                        CheckItemDto(
                                            id = "m-history-item-return-1",
                                            productId = null,
                                            barcode = "1001",
                                            name = "Товар X",
                                            quantity = 1.0,
                                            price = 1000L,
                                            discount = 0L,
                                            vatRate = "VAT_20",
                                            total = 1000L
                                        )
                                    ),
                                    createdAt = now + 3
                                )
                            )
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/reports/movement")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .param("period", "day")
                .param("since", since.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openingStock").value(10.0))
            .andExpect(jsonPath("$.income").value(15.0))
            .andExpect(jsonPath("$.sales").value(3.0))
            .andExpect(jsonPath("$.returns").value(1.0))
            .andExpect(jsonPath("$.writeoff").value(2.0))
            .andExpect(jsonPath("$.closingStock").value(21.0))
            .andExpect(jsonPath("$.items[0].name").value("Товар X"))
            .andExpect(jsonPath("$.items[0].income").value(10.0))
            .andExpect(jsonPath("$.items[0].sales").value(3.0))
            .andExpect(jsonPath("$.items[0].balance").value(12.0))
            .andExpect(jsonPath("$.items[1].name").value("Товар Y"))
            .andExpect(jsonPath("$.items[1].income").value(5.0))
            .andExpect(jsonPath("$.items[1].sales").value(0.0))
            .andExpect(jsonPath("$.items[1].balance").value(9.0))
    }

    @Test
    fun `GET movement report excludes foreign documents and checks`() {
        val token = loginAndGetToken()
        val foreignToken = loginAndGetToken(pin = "5678", deviceId = FOREIGN_DEVICE_ID)
        val since = System.currentTimeMillis()
        val now = since + 1_000

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DocumentDto(
                            type = "ACCEPTANCE",
                            items = listOf(
                                DocumentItemDto(productId = null, barcode = "2001", name = "Свой товар", quantity = 4.0, reason = null)
                            ),
                            timestamp = now
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .header("Authorization", "Bearer $foreignToken")
                .header("X-Device-Id", FOREIGN_DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        DocumentDto(
                            type = "ACCEPTANCE",
                            items = listOf(
                                DocumentItemDto(productId = null, barcode = "2002", name = "Чужой товар", quantity = 99.0, reason = null)
                            ),
                            timestamp = now + 1
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CheckSyncRequestDto(
                            checks = listOf(
                                CheckDto(
                                    id = "own-movement-check",
                                    localUuid = "own-movement-local",
                                    shiftId = SHIFT_M_ID,
                                    cashierId = CASHIER_ID,
                                    deviceId = DEVICE_ID,
                                    type = "SALE",
                                    fiscalSign = null,
                                    ffdVersion = "1.05",
                                    subtotal = 1000L,
                                    discount = 0L,
                                    total = 1000L,
                                    taxAmount = 200L,
                                    paymentType = "cash",
                                    items = listOf(
                                        CheckItemDto(
                                            id = "own-movement-item",
                                            productId = null,
                                            barcode = "2001",
                                            name = "Свой товар",
                                            quantity = 1.0,
                                            price = 1000L,
                                            discount = 0L,
                                            vatRate = "VAT_20",
                                            total = 1000L
                                        )
                                    ),
                                    createdAt = now + 2
                                )
                            )
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $foreignToken")
                .header("X-Device-Id", FOREIGN_DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CheckSyncRequestDto(
                            checks = listOf(
                                CheckDto(
                                    id = "foreign-movement-check",
                                    localUuid = "foreign-movement-local",
                                    shiftId = FOREIGN_SHIFT_ID,
                                    cashierId = FOREIGN_CASHIER_ID,
                                    deviceId = FOREIGN_DEVICE_ID,
                                    type = "SALE",
                                    fiscalSign = null,
                                    ffdVersion = "1.05",
                                    subtotal = 7000L,
                                    discount = 0L,
                                    total = 7000L,
                                    taxAmount = 1400L,
                                    paymentType = "cash",
                                    items = listOf(
                                        CheckItemDto(
                                            id = "foreign-movement-item",
                                            productId = null,
                                            barcode = "2002",
                                            name = "Чужой товар",
                                            quantity = 1.0,
                                            price = 7000L,
                                            discount = 0L,
                                            vatRate = "VAT_20",
                                            total = 7000L
                                        )
                                    ),
                                    createdAt = now + 3
                                )
                            )
                        )
                    )
                )
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/reports/movement")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .param("period", "day")
                .param("since", since.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.income").value(4.0))
            .andExpect(jsonPath("$.sales").value(1.0))
            .andExpect(jsonPath("$.closingStock").value(3.0))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Свой товар"))
    }

    private fun loginAndGetToken(pin: String = "1234", deviceId: String = DEVICE_ID): String {
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
        const val CASHIER_ID = "11111111-1111-1111-1111-111111111111"
        const val FOREIGN_CASHIER_ID = "22222222-2222-2222-2222-222222222222"
        const val DEVICE_ID = "TEST-DEVICE"
        const val FOREIGN_DEVICE_ID = "FOREIGN-DEVICE"
        const val SHIFT_1_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val SHIFT_2_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val SHIFT_M_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        const val FOREIGN_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd"
    }
}
