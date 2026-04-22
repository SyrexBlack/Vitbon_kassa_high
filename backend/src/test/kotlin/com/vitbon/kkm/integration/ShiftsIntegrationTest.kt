package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.ShiftDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class ShiftsIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST shift then GET shifts returns persisted shift`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val shiftId = "33333333-3333-3333-3333-333333333333"
        val cashierId = "44444444-4444-4444-4444-444444444444"

        val shift = ShiftDto(
            id = shiftId,
            cashierId = cashierId,
            deviceId = "TEST-DEVICE",
            openedAt = now,
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )

        mockMvc.perform(
            post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shift))
        ).andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/shifts/$cashierId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(shiftId))
            .andExpect(jsonPath("$[0].cashierId").value(cashierId))
            .andExpect(jsonPath("$[0].deviceId").value("TEST-DEVICE"))
    }

    @Test
    fun `PUT shift close sets closedAt for existing shift`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val shiftId = "55555555-5555-5555-5555-555555555555"
        val cashierId = "66666666-6666-6666-6666-666666666666"

        val shift = ShiftDto(
            id = shiftId,
            cashierId = cashierId,
            deviceId = "TEST-DEVICE",
            openedAt = now,
            closedAt = null,
            totalCash = 1000L,
            totalCard = 2000L
        )

        mockMvc.perform(
            post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shift))
        ).andExpect(status().isOk)

        mockMvc.perform(put("/api/v1/shifts/$shiftId/close"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/shifts/$cashierId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(shiftId))
            .andExpect(jsonPath("$[0].closedAt").isNotEmpty)
    }
}
