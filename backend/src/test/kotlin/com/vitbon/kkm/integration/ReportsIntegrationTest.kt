package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.api.dto.DocumentItemDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class ReportsIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `GET reports returns aggregated totals for synced sale checks`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()

        val syncRequest = CheckSyncRequestDto(
            checks = listOf(
                CheckDto(
                    id = "r-check-1",
                    localUuid = "r-local-1",
                    shiftId = "shift-1",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                    shiftId = "shift-1",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                    shiftId = "shift-1",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                    shiftId = "shift-2",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(syncRequest))
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/reports").param("period", "day").param("shiftId", "shift-1"))
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
    }

    @Test
    fun `GET movement report returns stock flow totals and item details`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(writeoffDoc))
        ).andExpect(status().isOk)

        val checks = CheckSyncRequestDto(
            checks = listOf(
                CheckDto(
                    id = "m-sale-1",
                    localUuid = "m-sale-local-1",
                    shiftId = "shift-m",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                    shiftId = "shift-m",
                    cashierId = "cashier-demo-1",
                    deviceId = "TEST-DEVICE",
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(checks))
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/reports/movement").param("period", "day").param("since", since.toString()))
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
}
