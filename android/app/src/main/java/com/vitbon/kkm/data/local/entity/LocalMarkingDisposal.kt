package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "marking_disposals",
    indices = [Index(value = ["code"], unique = true)]
)
data class LocalMarkingDisposal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val checkId: String,
    val disposedAt: Long = System.currentTimeMillis(),
    val status: String // "SUCCESS" | "FAILED"
)