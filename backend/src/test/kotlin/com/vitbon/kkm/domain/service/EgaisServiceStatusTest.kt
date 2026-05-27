package com.vitbon.kkm.domain.service

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class EgaisServiceStatusTest {

    @Test
    fun `status returns available when both egais endpoints are reachable`() {
        val server = HttpServer.create(InetSocketAddress(0), 0).apply { start() }

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val service = EgaisService(
                externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
                incomingUrl = "$baseUrl/incoming",
                taraUrl = "$baseUrl/tara",
                requestTimeoutSeconds = 1
            )

            assertTrue(service.status().available)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `status returns unavailable when egais route is missing`() {
        val service = EgaisService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            incomingUrl = "",
            taraUrl = "http://127.0.0.1:65535/tara",
            requestTimeoutSeconds = 1
        )

        assertFalse(service.status().available)
    }
}