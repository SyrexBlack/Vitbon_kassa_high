package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.LicenseCheckResponseDto
import com.vitbon.kkm.domain.persistence.DeviceLicenseEntity
import com.vitbon.kkm.domain.persistence.DeviceLicenseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional

class LicenseServiceTest {

    private val repository: DeviceLicenseRepository = mock(DeviceLicenseRepository::class.java)
    private val service = LicenseService(repository)

    @Test
    fun `check returns UNLICENSED when no row exists`() {
        `when`(repository.findById("DEV-1")).thenReturn(Optional.empty())

        val result = service.check("DEV-1")

        assertEquals("UNLICENSED", result.status)
        assertNull(result.expiryDate)
        assertNull(result.graceUntil)
    }

    @Test
    fun `check returns ACTIVE for ACTIVE row with future expiry and no grace`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val entity = DeviceLicenseEntity(
            deviceId = "DEV-A",
            status = "ACTIVE",
            expiryDate = now.plusDays(10),
            graceUntil = null,
            updatedAt = now
        )
        `when`(repository.findById("DEV-A")).thenReturn(Optional.of(entity))

        val result: LicenseCheckResponseDto = service.check("DEV-A")

        assertEquals("ACTIVE", result.status)
        assertEquals(entity.expiryDate!!.toInstant().toEpochMilli(), result.expiryDate)
    }

    @Test
    fun `check returns GRACE_PERIOD for EXPIRED row with future grace`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val entity = DeviceLicenseEntity(
            deviceId = "DEV-G",
            status = "EXPIRED",
            expiryDate = now.minusDays(1),
            graceUntil = now.plusDays(2),
            updatedAt = now
        )
        `when`(repository.findById("DEV-G")).thenReturn(Optional.of(entity))

        val result = service.check("DEV-G")

        assertEquals("GRACE_PERIOD", result.status)
        assertEquals(entity.graceUntil!!.toInstant().toEpochMilli(), result.graceUntil)
    }

    @Test
    fun `check returns EXPIRED when grace period is over`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val entity = DeviceLicenseEntity(
            deviceId = "DEV-E",
            status = "EXPIRED",
            expiryDate = now.minusDays(10),
            graceUntil = now.minusDays(1),
            updatedAt = now
        )
        `when`(repository.findById("DEV-E")).thenReturn(Optional.of(entity))

        val result = service.check("DEV-E")

        assertEquals("EXPIRED", result.status)
    }

    @Test
    fun `check downgrades stale ACTIVE row to EXPIRED when expiry in the past`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val entity = DeviceLicenseEntity(
            deviceId = "DEV-AE",
            status = "ACTIVE",
            expiryDate = now.minusSeconds(60),
            graceUntil = null,
            updatedAt = now
        )
        `when`(repository.findById("DEV-AE")).thenReturn(Optional.of(entity))

        val result = service.check("DEV-AE")

        assertEquals("EXPIRED", result.status)
    }

    @Test
    fun `upsert stores the row via repository and returns the saved entity`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val entity = DeviceLicenseEntity(
            deviceId = "DEV-UP",
            status = "ACTIVE",
            expiryDate = now.plusDays(30),
            graceUntil = null,
            updatedAt = now
        )
        `when`(repository.save(entity)).thenReturn(entity)

        val saved = service.upsert(
            deviceId = "DEV-UP",
            status = "ACTIVE",
            expiryDate = now.plusDays(30),
            graceUntil = null
        )

        assertEquals("DEV-UP", saved.deviceId)
        assertEquals("ACTIVE", saved.status)
    }
}