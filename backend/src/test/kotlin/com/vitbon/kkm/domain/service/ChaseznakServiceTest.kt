package com.vitbon.kkm.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException

class ChaseznakServiceTest {

    private val service = ChaseznakService(
        externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
        sellUrl = "http://127.0.0.1:1/sell",
        validateUrl = "http://127.0.0.1:1/validate",
        verifyAgeUrl = "http://127.0.0.1:1/verify-age"
    )

    @Test
    fun `validate returns 503 when upstream is unreachable`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.validate("""{"code":"010460123456789021SERIAL"}""")
        }
        assertEquals(503, exception.statusCode.value())
        assertTrue(exception.reason?.startsWith("Chestny ZNAK") == true)
    }

    @Test
    fun `processSell returns 503 with Chestny ZNAK label when unreachable`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.processSell("""{"code":"010460123456789021SERIAL","checkId":"CHK-1"}""")
        }
        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `verifyAge returns 503 with Age verification label when unreachable`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.verifyAge("""{"qrData":"MAX-ID-QR"}""")
        }
        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `validate throws 503 when validateUrl is blank`() {
        val blank = ChaseznakService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            sellUrl = "http://127.0.0.1:1/sell",
            validateUrl = "",
            verifyAgeUrl = "http://127.0.0.1:1/verify-age"
        )
        val exception = assertThrows(ResponseStatusException::class.java) {
            blank.validate("{}")
        }
        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `processSell throws 503 when sellUrl is blank`() {
        val blank = ChaseznakService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            sellUrl = "",
            validateUrl = "http://127.0.0.1:1/validate",
            verifyAgeUrl = "http://127.0.0.1:1/verify-age"
        )
        val exception = assertThrows(ResponseStatusException::class.java) {
            blank.processSell("{}")
        }
        assertEquals(503, exception.statusCode.value())
    }

    @Test
    fun `verifyAge throws 503 when verifyAgeUrl is blank`() {
        val blank = ChaseznakService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            sellUrl = "http://127.0.0.1:1/sell",
            validateUrl = "http://127.0.0.1:1/validate",
            verifyAgeUrl = ""
        )
        val exception = assertThrows(ResponseStatusException::class.java) {
            blank.verifyAge("{}")
        }
        assertEquals(503, exception.statusCode.value())
    }
}
