package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class CheckSyncIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST checks-sync — accepts valid check and returns processed count`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

        val request = CheckSyncRequestDto(listOf(
            CheckDto(
                id = "test-uuid-1",
                localUuid = "local-uuid-1",
                shiftId = null,
                cashierId = null,
                deviceId = "TEST-DEVICE",
                type = "SALE",
                fiscalSign = null,
                ffdVersion = "1.05",
                subtotal = 10000L,
                discount = 0L,
                total = 10000L,
                taxAmount = 2200L,
                paymentType = "cash",
                items = listOf(
                    CheckItemDto(
                        id = "item-1",
                        productId = null,
                        barcode = "4601234567890",
                        name = "Тестовый товар",
                        quantity = 1.0,
                        price = 10000L,
                        discount = 0L,
                        vatRate = "VAT_22",
                        total = 10000L
                    )
                ),
                createdAt = System.currentTimeMillis()
            )
        ))

        mockMvc.perform(
            post("/api/v1/checks/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.failed").isArray)
            .andExpect(jsonPath("$.failed").isEmpty)
    }

    @Test
    fun `CheckSyncResponseDto — correctly reports failed checks`() {
        val response = CheckSyncResponseDto(
            processed = 1,
            failed = listOf(FailedCheckDto("bad-uuid", "Invalid fiscal sign"))
        )
        assertEquals(1, response.processed)
        assertEquals(1, response.failed.size)
        assertEquals("bad-uuid", response.failed[0].localUuid)
    }

    @Test
    fun `FailsDto — empty list means all processed`() {
        val response = CheckSyncResponseDto(processed = 5, failed = emptyList())
        assertEquals(5, response.processed)
        assertTrue(response.failed.isEmpty())
    }
}
