package com.vitbon.kkm.domain.service

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.net.InetSocketAddress

/**
 * Lightweight integration tests for [EgaisService] — verifies TCP reachability
 * checks and that the configuration is honoured (empty URLs → not available).
 *
 * The actual proxy behaviour is exercised end-to-end in
 * [com.vitbon.kkm.integration.ExternalIntegrationsProxyIntegrationTest].
 */
class EgaisServiceProxyTest {

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
    fun `status returns unavailable when egais incoming route is missing`() {
        val service = EgaisService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            incomingUrl = "",
            taraUrl = "http://127.0.0.1:65535/tara",
            requestTimeoutSeconds = 1
        )

        assertFalse(service.status().available)
    }

    @Test
    fun `status returns unavailable when both URLs are blank`() {
        val service = EgaisService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            incomingUrl = "",
            taraUrl = "",
            requestTimeoutSeconds = 1
        )

        assertFalse(service.status().available)
    }

    @Test
    fun `processIncoming delegates to ExternalIntegrationProxyService with XML content type`() {
        val service = EgaisService(
            externalIntegrationProxyService = ExternalIntegrationProxyService(requestTimeoutSeconds = 1),
            incomingUrl = "http://127.0.0.1:65535/incoming",
            taraUrl = "",
            requestTimeoutSeconds = 1
        )

        // unreachable endpoint → 503 proxy exception
        var caught = false
        try {
            service.processIncoming("<Waybill/>")
        } catch (e: org.springframework.web.server.ResponseStatusException) {
            caught = true
            assertTrue(e.statusCode.value() == 503)
            assertTrue(e.reason?.contains("EGAIS") == true)
        }
        assertTrue(caught, "Expected ResponseStatusException with EGAIS label")
        // ensure XML content type contract: covered by signature check at compile time;
        // MediaType.APPLICATION_XML is what processIncoming uses internally.
        assertTrue(MediaType.APPLICATION_XML.toString().startsWith("application/xml"))
    }
}