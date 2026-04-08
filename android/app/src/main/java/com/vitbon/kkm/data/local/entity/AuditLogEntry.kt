package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log")
data class AuditLogEntry(
    @PrimaryKey val id: String,
    val cashierId: String?,
    val deviceId: String?,
    val action: String,
    val details: String?,
    val timestamp: Long
)
