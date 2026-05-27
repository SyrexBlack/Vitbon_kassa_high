package com.vitbon.kkm.domain.service.security

import com.vitbon.kkm.api.dto.AuditSyncEntryDto
import com.vitbon.kkm.api.dto.AuditSyncRequestDto
import com.vitbon.kkm.api.dto.AuditSyncResponseDto
import com.vitbon.kkm.api.dto.FailedAuditSyncDto
import com.vitbon.kkm.domain.persistence.AuditEventEntity
import com.vitbon.kkm.domain.persistence.AuditEventRepository
import com.vitbon.kkm.domain.persistence.CashierRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class AuditService(
    private val auditEventRepository: AuditEventRepository,
    private val cashierRepository: CashierRepository
) {
    fun write(
        actorId: UUID?,
        actorRole: String?,
        deviceId: String?,
        sessionId: UUID?,
        action: String,
        target: String?,
        result: String,
        reason: String?
    ) {
        auditEventRepository.save(
            AuditEventEntity(
                id = UUID.randomUUID(),
                actorId = actorId,
                actorRole = actorRole,
                deviceId = deviceId,
                sessionId = sessionId,
                action = action,
                target = target,
                result = result,
                reason = reason,
                createdAt = OffsetDateTime.now()
            )
        )
    }

    fun ingestBufferedEvents(
        principal: AuthPrincipal,
        request: AuditSyncRequestDto
    ): AuditSyncResponseDto {
        var processed = 0
        val failed = mutableListOf<FailedAuditSyncDto>()

        request.events.forEach { entry ->
            val eventId = runCatching { UUID.fromString(entry.id) }
                .getOrElse {
                    failed += FailedAuditSyncDto(entry.id, "INVALID_ID")
                    return@forEach
                }

            if (auditEventRepository.existsById(eventId)) {
                processed += 1
                return@forEach
            }

            val deviceId = entry.deviceId ?: principal.deviceId
            if (deviceId != principal.deviceId) {
                failed += FailedAuditSyncDto(entry.id, "DEVICE_MISMATCH")
                return@forEach
            }

            val actorId = parseActorId(entry)
                ?: run {
                    if (entry.cashierId != null) {
                        failed += FailedAuditSyncDto(entry.id, "INVALID_CASHIER_ID")
                    }
                    null
                }
            if (entry.cashierId != null && actorId == null) return@forEach

            val (result, reason) = parseResult(entry.details)
            val actorRole = actorId?.let { cashierRepository.findById(it).orElse(null)?.role }
            auditEventRepository.save(
                AuditEventEntity(
                    id = eventId,
                    actorId = actorId,
                    actorRole = actorRole,
                    deviceId = deviceId,
                    sessionId = null,
                    action = entry.action,
                    target = inferTarget(entry.action),
                    result = result,
                    reason = reason,
                    createdAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(entry.timestamp), ZoneOffset.UTC)
                )
            )
            processed += 1
        }

        return AuditSyncResponseDto(processed = processed, failed = failed)
    }

    private fun parseActorId(entry: AuditSyncEntryDto): UUID? {
        val rawCashierId = entry.cashierId ?: return null
        return runCatching { UUID.fromString(rawCashierId) }.getOrNull()
    }

    private fun parseResult(details: String?): Pair<String, String?> {
        if (details.isNullOrBlank()) return "FAIL" to "MISSING_DETAILS"

        val result = details.substringBefore(':').uppercase()
        val reason = details.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        return when (result) {
            "SUCCESS", "DENY", "FAIL", "ALLOW" -> result to reason
            else -> "FAIL" to details
        }
    }

    private fun inferTarget(action: String): String {
        return if (action.startsWith("auth.emergency")) {
            "emergency_mode"
        } else {
            "android.local_buffer"
        }
    }
}
