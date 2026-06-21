package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.LicenseCheckResponseDto
import com.vitbon.kkm.domain.persistence.DeviceLicenseEntity
import com.vitbon.kkm.domain.persistence.DeviceLicenseRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val LICENSE_STATUS_ACTIVE = "ACTIVE"
private const val LICENSE_STATUS_EXPIRED = "EXPIRED"
private const val LICENSE_STATUS_GRACE_PERIOD = "GRACE_PERIOD"
private const val LICENSE_STATUS_UNLICENSED = "UNLICENSED"

/**
 * License lookup service.
 *
 * Resolves a device's license status against the [device_licenses] table.
 * The status reported back to the client reflects three sources of truth:
 *  - persisted status (`ACTIVE` / `EXPIRED` / `GRACE_PERIOD`)
 *  - `expiry_date` (compared to `now`)
 *  - `grace_until` (compared to `now`)
 *
 * Precedence:
 *  1. Row missing → `UNLICENSED`
 *  2. `ACTIVE` + not expired by date → `ACTIVE`
 *  3. `EXPIRED`/`GRACE_PERIOD` with grace still in the future → `GRACE_PERIOD`
 *  4. Anything else → `EXPIRED`
 */
@Service
class LicenseService(
    private val deviceLicenseRepository: DeviceLicenseRepository
) {
    fun check(deviceId: String): LicenseCheckResponseDto {
        val row = deviceLicenseRepository.findById(deviceId).orElse(null)
            ?: return LicenseCheckResponseDto(
                status = LICENSE_STATUS_UNLICENSED,
                expiryDate = null,
                graceUntil = null
            )

        val now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        val graceActive = row.graceUntil?.let { !it.isBefore(now) } == true
        val expiredByDate = row.expiryDate?.let { !it.isAfter(now) } == true

        val resolvedStatus = when {
            row.status == LICENSE_STATUS_ACTIVE && !expiredByDate -> LICENSE_STATUS_ACTIVE
            row.status != LICENSE_STATUS_ACTIVE && graceActive -> LICENSE_STATUS_GRACE_PERIOD
            else -> LICENSE_STATUS_EXPIRED
        }

        return LicenseCheckResponseDto(
            status = resolvedStatus,
            expiryDate = row.expiryDate?.toInstant()?.toEpochMilli(),
            graceUntil = row.graceUntil?.toInstant()?.toEpochMilli()
        )
    }

    /** Used by admin/seed flows — exposes raw repository access without changing the public API. */
    internal fun upsert(
        deviceId: String,
        status: String,
        expiryDate: OffsetDateTime?,
        graceUntil: OffsetDateTime?
    ): DeviceLicenseEntity {
        val updated = DeviceLicenseEntity(
            deviceId = deviceId,
            status = status,
            expiryDate = expiryDate,
            graceUntil = graceUntil,
            updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        )
        return deviceLicenseRepository.save(updated)
    }
}