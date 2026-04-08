package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cashiers")
data class LocalCashier(
    @PrimaryKey val id: String,
    val name: String,
    val pinHash: String,
    val role: String,
    val createdAt: Long
)
