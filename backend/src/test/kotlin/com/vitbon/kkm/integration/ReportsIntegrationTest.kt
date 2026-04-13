package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
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
            .andExpect(jsonPath("$.totalRevenue").value(15000))
            .andExpect(jsonPath("$.cashRevenue").value(10000))
            .andExpect(jsonPath("$.cardRevenue").value(5000))
            .andExpect(jsonPath("$.averageCheck").value(7500))
    }
}
