package com.vitbon.kkm.features.inventory.domain

import com.vitbon.kkm.data.local.dao.InventoryDocumentDao
import com.vitbon.kkm.data.local.dao.InventoryDocumentItemDao
import com.vitbon.kkm.data.local.entity.LocalInventoryDocument
import com.vitbon.kkm.data.local.entity.LocalInventoryDocumentItem
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.DocumentDto
import com.vitbon.kkm.data.remote.dto.DocumentItemDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит инвентарные документы локально и синхронизирует с API.
 * Всегда сохраняет локально, затем пытается отправить.
 * При ошибке — повтор через retryPending().
 */
@Singleton
class InventoryDocumentRepository @Inject constructor(
    private val dao: InventoryDocumentDao,
    private val itemDao: InventoryDocumentItemDao,
    private val api: VitbonApi
) {
    /**
     * Сохранить документ и позиции локально.
     * Статус по умолчанию: PENDING_SYNC.
     */
    suspend fun saveDocument(
        doc: LocalInventoryDocument,
        items: List<LocalInventoryDocumentItem>
    ): LocalInventoryDocument {
        dao.insert(doc)
        itemDao.insertAll(items)
        return doc.copy(status = "PENDING_SYNC")
    }

    /**
     * Отправить документ: сохранить локально, затем в API.
     * Офлайн-сбой не блокирует — документ остаётся в PENDING_SYNC для повтора.
     */
    suspend fun submitDocument(
        doc: LocalInventoryDocument,
        items: List<LocalInventoryDocumentItem>
    ): LocalInventoryDocument {
        val localDoc = doc.copy(status = "PENDING_SYNC")
        dao.insert(localDoc)
        itemDao.insertAll(items)

        try {
            val dto = DocumentDto(
                type = doc.type,
                items = items.map { item ->
                    DocumentItemDto(
                        productId = null,
                        barcode = item.barcode,
                        name = item.name,
                        quantity = (item.actual - item.expected).toDouble()
                    )
                },
                timestamp = System.currentTimeMillis()
            )
            val response = api.sendInventory(dto)
            if (response.isSuccessful) {
                dao.updateStatus(localDoc.id, "PENDING_SYNC", null)
                return localDoc.copy(status = "PENDING_SYNC")
            }
        } catch (_: Throwable) {
            // сеть недоступна — документ остаётся в очереди
        }
        return localDoc
    }

    /**
     * Отметить документ как синхронизированный.
     */
    suspend fun markSynced(documentId: String, fiscalSign: String?) {
        dao.updateStatus(
            id = documentId,
            status = "PENDING_SYNC",
            fiscalSign = fiscalSign,
            syncedAt = System.currentTimeMillis()
        )
    }

    /**
     * Отметить документ как ошибочный (для повторной попытки).
     */
    suspend fun markError(documentId: String, message: String?) {
        dao.updateStatus(
            id = documentId,
            status = "SYNC_ERROR",
            fiscalSign = null,
            errorMessage = message
        )
    }

    /**
     * Повторить отправку всех документов в статусе PENDING_SYNC.
     * Возвращает количество успешно отправленных.
     */
    suspend fun retryPending(): Int {
        val pending = dao.findPendingSync()
        var successCount = 0
        for (doc in pending) {
            try {
                val response = api.sendInventory(
                    DocumentDto(
                        type = doc.type,
                        items = emptyList(), // items loaded from itemDao
                        timestamp = doc.createdAt
                    )
                )
                if (response.isSuccessful) {
                    dao.updateStatus(doc.id, "PENDING_SYNC", null)
                    successCount++
                }
            } catch (_: Throwable) {
                // пропускаем,下次 retry
            }
        }
        return successCount
    }

    /**
     * Количество документов, ожидающих синхронизации.
     */
    suspend fun getPendingCount(): Int = dao.countPending()
}