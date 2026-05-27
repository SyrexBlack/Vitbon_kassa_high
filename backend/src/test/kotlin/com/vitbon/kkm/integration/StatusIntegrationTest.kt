package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.CheckItemDto
import com.vitbon.kkm.api.dto.CheckSyncRequestDto
import com.vitbon.kkm.api.dto.LoginRequestDto
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.persistence.CheckRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class StatusIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @Autowired
    lateinit var checkRepository: CheckRepository

    @Autowired
    lateinit var dataSource: DataSource

    @BeforeEach
    fun setUpFixture() {
        authSessionRepository.deleteAll()
        checkRepository.deleteAll()
        cashierRepository.deleteAll()

        cashierRepository.saveAll(
            listOf(
                CashierEntity(
                    id = UUID.fromString(ADMIN_ID),
                    name = "Администратор",
                    pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                    role = "ADMIN",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                ),
                CashierEntity(
                    id = UUID.fromString(FOREIGN_ADMIN_ID),
                    name = "Чужой администратор",
                    pinHash = "f8638b979b2f4f793ddb6dbd197e0ee25a7a6ea32b0ae22f5e3c5d119d839e75",
                    role = "ADMIN",
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
        )
    }

    @Test
    fun `statuses endpoint returns device scoped telemetry for authorized admin`() {
        val ownDeviceId = "DEVICE-STATUS-OWN"
        val foreignDeviceId = "DEVICE-STATUS-FOREIGN"
        val ownToken = loginAndGetToken(pin = "1234", deviceId = ownDeviceId)
        val foreignToken = loginAndGetToken(pin = "5678", deviceId = foreignDeviceId)
        val pendingCreatedAt = System.currentTimeMillis() - 5_000L
        val ownLatestCreatedAt = pendingCreatedAt + 1_000L
        val foreignLatestCreatedAt = ownLatestCreatedAt + 5_000L

        upsertLicense(
            deviceId = ownDeviceId,
            status = "ACTIVE",
            expiryDate = System.currentTimeMillis() + 7L * 24 * 3600 * 1000,
            graceUntil = null
        )

        syncCheck(
            token = ownToken,
            deviceId = ownDeviceId,
            checkId = "status-own-pending",
            localUuid = "status-own-pending-local",
            fiscalSign = null,
            createdAt = pendingCreatedAt
        )
        syncCheck(
            token = ownToken,
            deviceId = ownDeviceId,
            checkId = "status-own-synced",
            localUuid = "status-own-synced-local",
            fiscalSign = "fs-own-ok",
            createdAt = ownLatestCreatedAt
        )
        syncCheck(
            token = foreignToken,
            deviceId = foreignDeviceId,
            checkId = "status-foreign-pending",
            localUuid = "status-foreign-pending-local",
            fiscalSign = null,
            createdAt = foreignLatestCreatedAt
        )

        mockMvc.perform(
            get("/api/v1/statuses")
                .header("Authorization", "Bearer $ownToken")
                .header("X-Device-Id", ownDeviceId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ofdQueueLength").value(1))
            .andExpect(jsonPath("$.lastSyncTimestamp").value(ownLatestCreatedAt))
            .andExpect(jsonPath("$.cloudServerOk").value(true))
            .andExpect(jsonPath("$.licenseStatus").value("ACTIVE"))
    }

    private fun syncCheck(
        token: String,
        deviceId: String,
        checkId: String,
        localUuid: String,
        fiscalSign: String?,
        createdAt: Long
    ) {
        mockMvc.perform(
            post("/api/v1/checks/sync")
                .header("Authorization", "Bearer $token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CheckSyncRequestDto(
                            checks = listOf(
                                CheckDto(
                                    id = checkId,
                                    localUuid = localUuid,
                                    shiftId = null,
                                    cashierId = ADMIN_ID,
                                    deviceId = deviceId,
                                    type = "SALE",
                                    fiscalSign = fiscalSign,
                                    ffdVersion = "1.05",
                                    subtotal = 1000L,
                                    discount = 0L,
                                    total = 1000L,
                                    taxAmount = 200L,
                                    paymentType = "cash",
                                    items = listOf(
                                        CheckItemDto(
                                            id = "$checkId-item",
                                            productId = null,
                                            barcode = "4607000000001",
                                            name = "Статус товар",
                                            quantity = 1.0,
                                            price = 1000L,
                                            discount = 0L,
                                            vatRate = "VAT_20",
                                            total = 1000L
                                        )
                                    ),
                                    createdAt = createdAt
                                )
                            )
                        )
                    )
                )
        ).andExpect(status().isOk)
    }

    private fun loginAndGetToken(pin: String, deviceId: String): String {
        val loginBody = LoginRequestDto(pin = pin, deviceId = deviceId)
        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody))
        ).andExpect(status().isOk)
            .andReturn()

        val node = objectMapper.readTree(loginResponse.response.contentAsString)
        return node.get("token").asText()
    }

    private fun upsertLicense(
        deviceId: String,
        status: String,
        expiryDate: Long?,
        graceUntil: Long?
    ) {
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM device_licenses WHERE device_id = ?").use { ps ->
                ps.setString(1, deviceId)
                ps.executeUpdate()
            }

            conn.prepareStatement(
                """
                INSERT INTO device_licenses (device_id, status, expiry_date, grace_until, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, deviceId)
                ps.setString(2, status)
                ps.setTimestamp(3, expiryDate?.let { Timestamp.from(Instant.ofEpochMilli(it)) })
                ps.setTimestamp(4, graceUntil?.let { Timestamp.from(Instant.ofEpochMilli(it)) })
                ps.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(System.currentTimeMillis())))
                ps.executeUpdate()
            }
        }
    }

    private fun withConnection(block: (Connection) -> Unit) {
        dataSource.connection.use(block)
    }

    companion object {
        private const val ADMIN_ID = "11111111-1111-1111-1111-111111111111"
        private const val FOREIGN_ADMIN_ID = "22222222-2222-2222-2222-222222222222"
    }
}