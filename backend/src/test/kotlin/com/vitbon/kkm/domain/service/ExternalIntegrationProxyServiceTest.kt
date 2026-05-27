package com.vitbon.kkm.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException

class ExternalIntegrationProxyServiceTest {

    @Test
    fun `forwardPost returns 503 when upstream is unreachable`() {
        val service = ExternalIntegrationProxyService(requestTimeoutSeconds = 1)

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.forwardPost(
                endpointUrl = "http://127.0.0.1:1/unavailable",
                payload = "{}",
                contentType = MediaType.APPLICATION_JSON,
                integrationName = "Chestny ZNAK"
            )
        }

        assertEquals(503, exception.statusCode.value())
    }
}