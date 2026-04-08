package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"])]
)
data class LocalProduct(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String,
    val article: String?,
    val price: Long,
    val vatRate: String,
    val categoryId: String?,
    val stock: Double,
    val egaisFlag: Boolean,
    val chaseznakFlag: Boolean,
    val updatedAt: Long
)
