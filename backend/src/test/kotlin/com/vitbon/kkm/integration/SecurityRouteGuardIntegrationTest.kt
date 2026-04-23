package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LoginRequestDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRouteGuardIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `checks endpoint returns 401 without bearer token`() {
        mockMvc.perform(get("/api/v1/checks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `statuses endpoint returns 403 for cashier token`() {
        val loginBody = LoginRequestDto(pin = "1111", deviceId = "DEVICE-RBAC-1")
        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
            .andReturn()

        val node = objectMapper.readTree(loginResponse.response.contentAsString)
        val token = node.get("token").asText()

        mockMvc.perform(get("/api/v1/statuses").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
    }
}
