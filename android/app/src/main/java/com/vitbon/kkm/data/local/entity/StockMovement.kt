package com.vitbon.kkm.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["productId"])],
    foreignKeys = [ForeignKey(
        entity = LocalProduct::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class StockMovement(
    @PrimaryKey val id: String,
    val productId: String,
    val delta: Double, // negative for outflow (SALE, WRITEOFF), positive for inflow
    val type: String, // "SALE" | "RETURN" | "INCOME" | "WRITEOFF" | "INVENTORY"
    val referenceId: String?, // e.g. checkId, inventoryDocId
    val timestamp: Long
) {
    /**
     * INVENTORY movements set the ABSOLUTE stock, not a delta.
     * Ledger balance = stock at last INVENTORY + sum of all deltas since.
     */
    val isAnchor: Boolean get() = type == "INVENTORY"
}
