package com.vitbon.kkm.api.v1

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc

import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `health endpoint returns UP status`() {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/health"))
            .andExpect { result ->
                assertThat(result.response.status).isEqualTo(200)
                val body = result.response.contentAsString
                assertThat(body).contains("UP")
                assertThat(body).contains("vitbon-backend")
            }
    }

    @Test
    fun `live endpoint returns ALIVE status`() {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/health/live"))
            .andExpect { result ->
                assertThat(result.response.status).isEqualTo(200)
                assertThat(result.response.contentAsString).contains("ALIVE")
            }
    }

    @Test
    fun `ready endpoint returns READY status`() {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/health/ready"))
            .andExpect { result ->
                assertThat(result.response.status).isEqualTo(200)
                assertThat(result.response.contentAsString).contains("READY")
            }
    }
}