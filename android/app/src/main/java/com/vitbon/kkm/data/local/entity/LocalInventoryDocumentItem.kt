package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_document_items",
    foreignKeys = [
        ForeignKey(
            entity = LocalInventoryDocument::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class LocalInventoryDocumentItem(
    @PrimaryKey val id: String,
    val documentId: String,
    val barcode: String?,
    val name: String,
    val expected: Float,
    val actual: Float
) {
    val discrepancy: Float get() = actual - expected
}