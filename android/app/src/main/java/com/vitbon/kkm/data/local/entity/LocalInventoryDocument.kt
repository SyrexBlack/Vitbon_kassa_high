package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_documents")
data class LocalInventoryDocument(
    @PrimaryKey val id: String,
    val type: String, // "INVENTORY" | "ACCEPTANCE" | "WRITEOFF"
    val status: String, // "PENDING_SYNC" | "SYNCED" | "SYNC_ERROR"
    val createdAt: Long,
    val syncedAt: Long? = null,
    val fiscalSign: String? = null,
    val errorMessage: String? = null
)