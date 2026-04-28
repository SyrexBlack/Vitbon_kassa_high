package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.AuditLogDao
import com.vitbon.kkm.data.local.entity.AuditLogEntry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAuditBufferRepository @Inject constructor(
    private val auditLogDao: AuditLogDao
) {
    suspend fun enqueue(
        cashierId: String?,
        deviceId: String?,
        action: String,
        details: String?,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val id = UUID.randomUUID().toString()
        auditLogDao.insert(
            AuditLogEntry(
                id = id,
                cashierId = cashierId,
                deviceId = deviceId,
                action = action,
                details = details,
                timestamp = timestamp
            )
        )
        return id
    }

    suspend fun pending(limit: Int): List<AuditLogEntry> = auditLogDao.findPending(limit)

    suspend fun acknowledge(ids: List<String>) {
        if (ids.isEmpty()) return
        auditLogDao.deleteByIds(ids)
    }
}
