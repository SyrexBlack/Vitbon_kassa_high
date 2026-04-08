package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_items",
    foreignKeys = [ForeignKey(
        entity = LocalCheck::class,
        parentColumns = ["id"],
        childColumns = ["checkId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("checkId")]
)
data class LocalCheckItem(
    @PrimaryKey val id: String,
    val checkId: String,
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Long,
    val discount: Long,
    val vatRate: String,
    val total: Long
)
