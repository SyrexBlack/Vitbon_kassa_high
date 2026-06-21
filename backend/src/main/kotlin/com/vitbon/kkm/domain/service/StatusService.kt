package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.StatusResponseDto
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.DocumentRepository
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * Aggregates device-scoped telemetry for the /api/v1/statuses endpoint.
 *
 * Pulls:
 *  - OFD queue depth: how many checks for *this* cashier+device have an empty
 *    fiscalSign (i.e. fiscal doc printed but OFD ACK not yet received).
 *  - lastSyncTimestamp: max(check.createdAt, document.timestamp).
 *  - cloudServerOk: pessimistic (always true while this code path is reachable;
 *    a deeper health probe belongs in HealthController).
 *  - licenseStatus: forwarded from [LicenseService] for the *requesting* device.
 */
@Service
class StatusService(
    private val checkRepository: CheckRepository,
    private val documentRepository: DocumentRepository,
    private val licenseService: LicenseService
) {
    fun getStatuses(): StatusResponseDto {
        val principal = SecurityContextHolder.requirePrincipal()
        val checks = checkRepository.findAllByCashierIdAndDeviceId(principal.cashierId, principal.deviceId)
        val documents = documentRepository.findAllByCashierIdAndDeviceId(principal.cashierId, principal.deviceId)
        val lastCheckTs = checks.maxOfOrNull { it.createdAt.toInstant().toEpochMilli() } ?: 0L
        val lastDocumentTs = documents.maxOfOrNull { it.timestamp.toInstant().toEpochMilli() } ?: 0L

        return StatusResponseDto(
            ofdQueueLength = checks.count { it.fiscalSign.isNullOrBlank() },
            lastSyncTimestamp = maxOf(lastCheckTs, lastDocumentTs),
            cloudServerOk = true,
            licenseStatus = licenseService.check(principal.deviceId).status
        )
    }
}
