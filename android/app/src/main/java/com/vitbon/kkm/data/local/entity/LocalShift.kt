package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shifts")
data class LocalShift(
    @PrimaryKey val id: String,
    val cashierId: String,
    val deviceId: String,
    val openedAt: Long,
    val closedAt: Long?,
    val totalCash: Long,
    val totalCard: Long
)
