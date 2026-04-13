package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.api.dto.DocumentItemDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class DocumentsIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST documents acceptance accepts valid payload`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents writeoff accepts valid payload with reason`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents inventory accepts valid payload`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

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
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST documents acceptance rejects malformed payload`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ACCEPTANCE\",\"items\":\"bad\",\"timestamp\":1}")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST documents acceptance rejects empty items`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

        val doc = DocumentDto(
            type = "ACCEPTANCE",
            items = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        mockMvc.perform(
            post("/api/v1/documents/acceptance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc))
        )
            .andExpect(status().isBadRequest)
    }
}
