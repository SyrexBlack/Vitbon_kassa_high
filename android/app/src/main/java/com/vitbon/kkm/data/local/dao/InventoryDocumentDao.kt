package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalInventoryDocument

@Dao
interface InventoryDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: LocalInventoryDocument)

    @Query("SELECT * FROM inventory_documents WHERE id = :id")
    suspend fun findById(id: String): LocalInventoryDocument?

    @Query("SELECT * FROM inventory_documents WHERE status = 'PENDING_SYNC'")
    suspend fun findPendingSync(): List<LocalInventoryDocument>

    @Query("SELECT COUNT(*) FROM inventory_documents WHERE status = 'PENDING_SYNC'")
    suspend fun countPending(): Int

    @Query("UPDATE inventory_documents SET status = :status, fiscalSign = :fiscalSign, errorMessage = :errorMessage, syncedAt = :syncedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, fiscalSign: String?, errorMessage: String? = null, syncedAt: Long? = null)
}