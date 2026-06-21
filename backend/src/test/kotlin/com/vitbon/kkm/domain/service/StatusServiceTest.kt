package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.LicenseCheckResponseDto
import com.vitbon.kkm.api.dto.StatusResponseDto
import com.vitbon.kkm.domain.persistence.CheckEntity
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.DocumentEntity
import com.vitbon.kkm.domain.persistence.DocumentRepository
import com.vitbon.kkm.domain.service.security.AuthPrincipal
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class StatusServiceTest {

    private val checkRepository: CheckRepository = mock(CheckRepository::class.java)
    private val documentRepository: DocumentRepository = mock(DocumentRepository::class.java)
    private val licenseService: LicenseService = mock(LicenseService::class.java)
    private val service = StatusService(checkRepository, documentRepository, licenseService)

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clear()
    }

    @Test
    fun `getStatuses aggregates OFD queue, last sync and license status for principal device`() {
        val principal = AuthPrincipal(
            cashierId = UUID.randomUUID(),
            role = "CASHIER",
            deviceId = "DEV-1",
            sessionId = UUID.randomUUID()
        )
        SecurityContextHolder.set(principal)

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val pending = makeCheck(fiscalSign = null, createdAt = now.minusMinutes(5))
        val synced = makeCheck(fiscalSign = "FS-1", createdAt = now.minusMinutes(1))
        val doc = makeDocument(timestamp = now.minusSeconds(30))

        `when`(checkRepository.findAllByCashierIdAndDeviceId(principal.cashierId, "DEV-1"))
            .thenReturn(listOf(pending, synced))
        `when`(documentRepository.findAllByCashierIdAndDeviceId(principal.cashierId, "DEV-1"))
            .thenReturn(listOf(doc))
        `when`(licenseService.check("DEV-1"))
            .thenReturn(LicenseCheckResponseDto("ACTIVE", expiryDate = null, graceUntil = null))

        val result: StatusResponseDto = service.getStatuses()

        assertEquals(1, result.ofdQueueLength) // only `pending` has empty fiscalSign
        assertEquals(doc.timestamp.toInstant().toEpochMilli(), result.lastSyncTimestamp)
        assertEquals(true, result.cloudServerOk)
        assertEquals("ACTIVE", result.licenseStatus)
    }

    @Test
    fun `getStatuses returns zero lastSyncTimestamp when no data exists`() {
        val principal = AuthPrincipal(
            cashierId = UUID.randomUUID(),
            role = "CASHIER",
            deviceId = "DEV-EMPTY",
            sessionId = UUID.randomUUID()
        )
        SecurityContextHolder.set(principal)

        `when`(checkRepository.findAllByCashierIdAndDeviceId(principal.cashierId, "DEV-EMPTY"))
            .thenReturn(emptyList())
        `when`(documentRepository.findAllByCashierIdAndDeviceId(principal.cashierId, "DEV-EMPTY"))
            .thenReturn(emptyList())
        `when`(licenseService.check("DEV-EMPTY"))
            .thenReturn(LicenseCheckResponseDto("UNLICENSED", null, null))

        val result = service.getStatuses()

        assertEquals(0, result.ofdQueueLength)
        assertEquals(0L, result.lastSyncTimestamp)
        assertEquals("UNLICENSED", result.licenseStatus)
    }

    private fun makeCheck(fiscalSign: String?, createdAt: OffsetDateTime): CheckEntity =
        CheckEntity(
            id = UUID.randomUUID(),
            localUuid = UUID.randomUUID().toString(),
            shiftId = null,
            cashierId = UUID.randomUUID(),
            deviceId = "DEV-1",
            type = "SALE",
            fiscalSign = fiscalSign,
            ffdVersion = "1.05",
            subtotal = 1000L,
            discount = 0L,
            total = 1000L,
            taxAmount = 200L,
            paymentType = "CASH",
            createdAt = createdAt
        )

    private fun makeDocument(timestamp: OffsetDateTime): DocumentEntity =
        DocumentEntity(
            id = UUID.randomUUID(),
            type = "INVENTORY",
            cashierId = UUID.randomUUID(),
            deviceId = "DEV-1",
            timestamp = timestamp
        )
}