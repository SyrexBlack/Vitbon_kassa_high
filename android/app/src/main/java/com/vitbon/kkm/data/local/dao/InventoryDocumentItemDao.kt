package com.vitbon.kkm.data.local.dao

import androidx.room.*
import com.vitbon.kkm.data.local.entity.LocalInventoryDocumentItem

@Dao
interface InventoryDocumentItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalInventoryDocumentItem>)

    @Query("SELECT * FROM inventory_document_items WHERE documentId = :documentId")
    suspend fun findByDocumentId(documentId: String): List<LocalInventoryDocumentItem>

    @Query("DELETE FROM inventory_document_items WHERE documentId = :documentId")
    suspend fun deleteByDocumentId(documentId: String)
}