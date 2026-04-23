package com.vitbon.kkm.domain.service.security

import com.vitbon.kkm.domain.persistence.AuditEventEntity
import com.vitbon.kkm.domain.persistence.AuditEventRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AuditService(
    private val auditEventRepository: AuditEventRepository
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
}
