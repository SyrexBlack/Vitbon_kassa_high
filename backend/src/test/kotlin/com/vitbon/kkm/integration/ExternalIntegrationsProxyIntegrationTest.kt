package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest
@AutoConfigureMockMvc
class ExternalIntegrationsProxyIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @BeforeEach
    fun resetStubs() {
        recordedRequests.clear()
        stubResponses.clear()
        authSessionRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString(CASHIER_ID),
                name = "Старший кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = "SENIOR_CASHIER",
                createdAt = OffsetDateTime.now(ZoneOffset.UTC)
            )
        )
    }

    @Test
    fun `egais incoming proxies xml payload and upstream response`() {
        val token = loginAndGetToken()

        stubResponses["/egais/incoming"] = StubResponse(
            status = 200,
            contentType = MediaType.APPLICATION_XML_VALUE,
            body = "<EgaisReply><WaybillId>WB-123</WaybillId></EgaisReply>"
        )

        mockMvc.perform(
            post("/api/v1/egais/incoming")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_XML)
                .content("<Waybill><Number>WB-123</Number></Waybill>")
        )
            .andExpect(status().isOk)
            .andExpect(content().string("<EgaisReply><WaybillId>WB-123</WaybillId></EgaisReply>"))

        val request = assertRecordedRequest("/egais/incoming")
        assertTrue(request.contentType.startsWith(MediaType.APPLICATION_XML_VALUE))
        assertEquals("<Waybill><Number>WB-123</Number></Waybill>", request.body)
    }

    @Test
    fun `egais status returns available when routes are configured and reachable`() {
        val token = loginAndGetToken()

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/egais/status")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
        )
            .andExpect(status().isOk)
            .andExpect(content().json("{\"available\":true}"))
    }

    @Test
    fun `egais tara proxies xml payload and upstream error status`() {
        val token = loginAndGetToken()

        stubResponses["/egais/tara"] = StubResponse(
            status = 409,
            contentType = MediaType.APPLICATION_XML_VALUE,
            body = "<Fault>duplicate act</Fault>"
        )

        mockMvc.perform(
            post("/api/v1/egais/tara")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_XML)
                .content("<ActChargeOnWrite><Identity>ACT-1</Identity></ActChargeOnWrite>")
        )
            .andExpect(status().isConflict)
            .andExpect(content().string("<Fault>duplicate act</Fault>"))

        val request = assertRecordedRequest("/egais/tara")
        assertTrue(request.contentType.startsWith(MediaType.APPLICATION_XML_VALUE))
        assertEquals("<ActChargeOnWrite><Identity>ACT-1</Identity></ActChargeOnWrite>", request.body)
    }

    @Test
    fun `chaseznak sell proxies json payload and upstream response`() {
        val token = loginAndGetToken()

        stubResponses["/chaseznak/sell"] = StubResponse(
            status = 202,
            contentType = MediaType.APPLICATION_JSON_VALUE,
            body = "{\"status\":\"accepted\"}"
        )

        mockMvc.perform(
            post("/api/v1/chaseznak/sell")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"010460123456789021SERIAL\",\"checkId\":\"CHK-1\"}")
        )
            .andExpect(status().isAccepted)
            .andExpect(content().json("{\"status\":\"accepted\"}"))

        val request = assertRecordedRequest("/chaseznak/sell")
        assertTrue(request.contentType.startsWith(MediaType.APPLICATION_JSON_VALUE))
        assertEquals("{\"code\":\"010460123456789021SERIAL\",\"checkId\":\"CHK-1\"}", request.body)
    }

    @Test
    fun `chaseznak validate proxies json payload and upstream response`() {
        val token = loginAndGetToken()

        stubResponses["/chaseznak/validate"] = StubResponse(
            status = 200,
            contentType = MediaType.APPLICATION_JSON_VALUE,
            body = "{\"barcode\":\"010460123456789021SERIAL\",\"status\":\"OK\",\"productName\":\"Товар ЧЗ\",\"expiryDate\":null,\"message\":null}"
        )

        mockMvc.perform(
            post("/api/v1/chaseznak/validate")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"010460123456789021SERIAL\"}")
        )
            .andExpect(status().isOk)
            .andExpect(content().json("{\"barcode\":\"010460123456789021SERIAL\",\"status\":\"OK\",\"productName\":\"Товар ЧЗ\",\"expiryDate\":null,\"message\":null}"))

        val request = assertRecordedRequest("/chaseznak/validate")
        assertTrue(request.contentType.startsWith(MediaType.APPLICATION_JSON_VALUE))
        assertEquals("{\"code\":\"010460123456789021SERIAL\"}", request.body)
    }

    @Test
    fun `chaseznak verify age proxies json payload and upstream response`() {
        val token = loginAndGetToken()

        stubResponses["/chaseznak/verify-age"] = StubResponse(
            status = 200,
            contentType = MediaType.APPLICATION_JSON_VALUE,
            body = "{\"allowed\":true,\"source\":\"max-id\"}"
        )

        mockMvc.perform(
            post("/api/v1/chaseznak/verify-age")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", DEVICE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrData\":\"MAX-ID-QR\"}")
        )
            .andExpect(status().isOk)
            .andExpect(content().json("{\"allowed\":true,\"source\":\"max-id\"}"))

        val request = assertRecordedRequest("/chaseznak/verify-age")
        assertTrue(request.contentType.startsWith(MediaType.APPLICATION_JSON_VALUE))
        assertEquals("{\"qrData\":\"MAX-ID-QR\"}", request.body)
    }

    private fun assertRecordedRequest(path: String): RecordedRequest {
        val request = recordedRequests[path]
        assertNotNull(request, "Expected request for $path")
        return request!!
    }

    private fun loginAndGetToken(): String {
        val loginBody = LoginRequestDto(pin = "1234", deviceId = DEVICE_ID)
        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
            .andReturn()

        return objectMapper.readTree(loginResponse.response.contentAsString)
            .get("token")
            .asText()
    }

    private companion object {
        const val CASHIER_ID = "11111111-1111-1111-1111-111111111111"
        const val DEVICE_ID = "TEST-DEVICE"
        private val stubResponses = ConcurrentHashMap<String, StubResponse>()
        private val recordedRequests = ConcurrentHashMap<String, RecordedRequest>()
        private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/") { exchange -> handleExchange(exchange) }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("integrations.egais.incoming-url") { serverUrl("/egais/incoming") }
            registry.add("integrations.egais.tara-url") { serverUrl("/egais/tara") }
            registry.add("integrations.chaseznak.sell-url") { serverUrl("/chaseznak/sell") }
            registry.add("integrations.chaseznak.validate-url") { serverUrl("/chaseznak/validate") }
            registry.add("integrations.chaseznak.verify-age-url") { serverUrl("/chaseznak/verify-age") }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.stop(0)
        }

        private fun handleExchange(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val body = exchange.requestBody.use {
                String(it.readAllBytes(), StandardCharsets.UTF_8)
            }
            recordedRequests[path] = RecordedRequest(
                contentType = exchange.requestHeaders.getFirst("Content-Type") ?: "",
                body = body
            )

            val response = stubResponses[path] ?: StubResponse(
                status = 404,
                contentType = MediaType.TEXT_PLAIN_VALUE,
                body = "missing stub for $path"
            )

            exchange.responseHeaders.set("Content-Type", response.contentType)
            val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(response.status, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }

        private fun serverUrl(path: String): String {
            return "http://127.0.0.1:${server.address.port}$path"
        }
    }

    private data class StubResponse(
        val status: Int,
        val contentType: String,
        val body: String
    )

    private data class RecordedRequest(
        val contentType: String,
        val body: String
    )
}