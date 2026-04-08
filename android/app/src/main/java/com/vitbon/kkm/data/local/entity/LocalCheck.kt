package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checks")
data class LocalCheck(
    @PrimaryKey val id: String,
    val localUuid: String,
    val shiftId: String?,
    val cashierId: String?,
    val deviceId: String,
    val type: String,
    val fiscalSign: String?,
    val ofdResponse: String?,
    val ffdVersion: String?,
    val status: String,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val taxAmount: Long,
    val paymentType: String?,
    val createdAt: Long,
    val syncedAt: Long?
)
