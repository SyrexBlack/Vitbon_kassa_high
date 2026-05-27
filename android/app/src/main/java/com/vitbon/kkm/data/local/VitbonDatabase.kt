package com.vitbon.kkm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vitbon.kkm.data.local.entity.*
import com.vitbon.kkm.data.local.dao.*

@Database(
    entities = [
        LocalCashier::class,
        LocalShift::class,
        LocalCheck::class,
        LocalCheckItem::class,
        LocalProduct::class,
        LocalCategory::class,
        AuditLogEntry::class,
        LocalMarkingDisposal::class,
        LocalInventoryDocument::class,
        LocalInventoryDocumentItem::class,
        StockMovement::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VitbonDatabase : RoomDatabase() {
    abstract fun cashierDao(): CashierDao
    abstract fun shiftDao(): ShiftDao
    abstract fun checkDao(): CheckDao
    abstract fun checkItemDao(): CheckItemDao
    abstract fun productDao(): ProductDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun markingDisposalDao(): MarkingDisposalDao
    abstract fun inventoryDocumentDao(): InventoryDocumentDao
    abstract fun inventoryDocumentItemDao(): InventoryDocumentItemDao
    abstract fun stockMovementDao(): StockMovementDao
}
