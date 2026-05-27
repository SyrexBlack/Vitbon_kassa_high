package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.entity.AuditLogEntry
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val api: VitbonApi,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao,
    private val productDao: ProductDao,
    private val auditBufferRepository: LocalAuditBufferRepository,
    private val syncPrefs: SyncPrefs
) {
    /** Наблюдение за количеством несинхронизированных чеков */
    fun observePendingCount(): Flow<Int> = checkDao.observePendingCount()

    /** Push: синхронизировать все чеки со статусом PENDING_SYNC */
    suspend fun syncChecks(): SyncResult {
        val pending = checkDao.findPendingSync()
        if (pending.isEmpty()) return SyncResult(0, 0)

        val checkDtos = pending.map { check ->
            val items = checkItemDao.findByCheckId(check.id).map { item ->
                CheckItemDto(
                    id = item.id,
                    productId = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    quantity = item.quantity,
                    price = item.price,
                    discount = item.discount,
                    vatRate = item.vatRate,
                    total = item.total
                )
            }

            CheckDto(
                id = check.id,
                localUuid = check.localUuid,
                shiftId = check.shiftId,
                cashierId = check.cashierId,
                deviceId = check.deviceId,
                type = check.type,
                fiscalSign = check.fiscalSign,
                ffdVersion = check.ffdVersion,
                subtotal = check.subtotal,
                discount = check.discount,
                total = check.total,
                taxAmount = check.taxAmount,
                paymentType = check.paymentType,
                items = items,
                createdAt = check.createdAt
            )
        }

        return try {
            val response = api.syncChecks(CheckSyncRequestDto(checkDtos))
            if (response.isSuccessful) {
                val body = response.body()!!
                val failedUuids = body.failed.map { it.localUuid }.toSet()
                body.failed.forEach { failed ->
                    val check = pending.find { it.localUuid == failed.localUuid }
                    if (check != null) {
                        checkDao.updateSyncStatus(check.id, "ERROR", check.fiscalSign, null, null)
                    }
                }

                val syncedAt = System.currentTimeMillis()
                pending.filterNot { it.localUuid in failedUuids }
                    .forEach { check ->
                        checkDao.markSynced(check.id, syncedAt)
                    }

                val successCount = pending.count { it.localUuid !in failedUuids }
                syncPrefs.lastSyncTimestamp = syncedAt
                SyncResult(successCount, body.failed.size)
            } else {
                SyncResult(0, pending.size)
            }
        } catch (e: Exception) {
            SyncResult(0, pending.size)
        }
    }

    /** Pull: получить обновления товаров с сервера */
    suspend fun syncProducts(): ProductSyncResult {
        return try {
            val response = api.getProducts(syncPrefs.lastProductSyncTimestamp)
            if (response.isSuccessful) {
                val body = response.body()!!
                val dtos = body.products
                val entities = dtos.map { dto ->
                    com.vitbon.kkm.data.local.entity.LocalProduct(
                        id = dto.id,
                        barcode = dto.barcode,
                        name = dto.name,
                        article = dto.article,
                        price = dto.price,
                        vatRate = dto.vatRate,
                        categoryId = dto.categoryId,
                        stock = dto.stock,
                        egaisFlag = dto.egaisFlag,
                        chaseznakFlag = dto.chaseznakFlag,
                        updatedAt = dto.updatedAt
                    )
                }
                val deletedCount = if (body.deletedIds.isEmpty()) 0 else productDao.deleteByIds(body.deletedIds)
                productDao.insertAll(entities)
                syncPrefs.lastProductSyncTimestamp = body.serverTimestamp
                ProductSyncResult(entities.size, deletedCount)
            } else {
                ProductSyncResult(0, 0)
            }
        } catch (e: Exception) {
            ProductSyncResult(0, 0)
        }
    }

    suspend fun syncAuditLogs(): SyncResult {
        val pending = auditBufferRepository.pending(AUDIT_BATCH_LIMIT)
        if (pending.isEmpty()) return SyncResult(0, 0)

        return try {
            val response = api.syncAudit(
                AuditSyncRequestDto(
                    events = pending.map { it.toAuditSyncEntryDto() }
                )
            )
            if (response.isSuccessful) {
                val body = response.body() ?: return SyncResult(0, pending.size)
                val failedIds = body.failed.map { it.id }.toSet()
                val acknowledgedIds = pending.map { it.id }.filterNot { it in failedIds }
                auditBufferRepository.acknowledge(acknowledgedIds)
                SyncResult(acknowledgedIds.size, failedIds.size)
            } else {
                SyncResult(0, pending.size)
            }
        } catch (e: Exception) {
            SyncResult(0, pending.size)
        }
    }

    private fun AuditLogEntry.toAuditSyncEntryDto(): AuditSyncEntryDto = AuditSyncEntryDto(
        id = id,
        cashierId = cashierId,
        deviceId = deviceId,
        action = action,
        details = details,
        timestamp = timestamp
    )

    private companion object {
        const val AUDIT_BATCH_LIMIT = 100
    }
}

data class SyncResult(val synced: Int, val failed: Int)
data class ProductSyncResult(val received: Int, val deleted: Int)
