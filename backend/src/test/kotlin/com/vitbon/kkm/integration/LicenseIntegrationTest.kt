package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.LicenseCheckRequestDto
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

@SpringBootTest
class LicenseIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var dataSource: DataSource

    @Test
    fun `license check returns ACTIVE for active device license`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val deviceId = "DEVICE-ACTIVE"

        upsertLicense(
            deviceId = deviceId,
            status = "ACTIVE",
            expiryDate = now + 10 * 24 * 3600 * 1000L,
            graceUntil = null
        )

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.expiryDate").isNumber)
            .andExpect(jsonPath("$.graceUntil").doesNotExist())
    }

    @Test
    fun `license check returns ACTIVE by default when no device row exists`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val deviceId = "DEVICE-NO-ROW"

        deleteLicense(deviceId)

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.expiryDate").doesNotExist())
            .andExpect(jsonPath("$.graceUntil").doesNotExist())
    }

    @Test
    fun `license check returns GRACE_PERIOD when license expired but grace is active`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val deviceId = "DEVICE-GRACE"

        upsertLicense(
            deviceId = deviceId,
            status = "EXPIRED",
            expiryDate = now - 24 * 3600 * 1000L,
            graceUntil = now + 2 * 24 * 3600 * 1000L
        )

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("GRACE_PERIOD"))
            .andExpect(jsonPath("$.graceUntil").isNumber)
    }

    @Test
    fun `license check returns GRACE_PERIOD when status is GRACE_PERIOD and grace is active`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val deviceId = "DEVICE-GRACE-ROW"

        upsertLicense(
            deviceId = deviceId,
            status = "GRACE_PERIOD",
            expiryDate = now - 2 * 24 * 3600 * 1000L,
            graceUntil = now + 2 * 24 * 3600 * 1000L
        )

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("GRACE_PERIOD"))
            .andExpect(jsonPath("$.graceUntil").isNumber)
    }

    @Test
    fun `license check returns EXPIRED when grace period is over`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val deviceId = "DEVICE-EXPIRED"

        upsertLicense(
            deviceId = deviceId,
            status = "EXPIRED",
            expiryDate = now - 10 * 24 * 3600 * 1000L,
            graceUntil = now - 1 * 24 * 3600 * 1000L
        )

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.graceUntil").isNumber)
    }

    @Test
    fun `license check returns EXPIRED for ACTIVE row with past expiry and no grace`() {
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val now = System.currentTimeMillis()
        val deviceId = "DEVICE-ACTIVE-EXPIRED"

        upsertLicense(
            deviceId = deviceId,
            status = "ACTIVE",
            expiryDate = now - 60_000L,
            graceUntil = null
        )

        mockMvc.perform(
            post("/api/v1/license/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LicenseCheckRequestDto(deviceId)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EXPIRED"))
    }


    private fun deleteLicense(deviceId: String) {
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM device_licenses WHERE device_id = ?").use { ps ->
                ps.setString(1, deviceId)
                ps.executeUpdate()
            }
        }
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
}
