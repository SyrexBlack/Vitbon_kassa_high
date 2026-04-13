package com.vitbon.kkm.core.sync

import android.content.Context
import com.vitbon.kkm.core.sync.worker.SyncDownWorker
import com.vitbon.kkm.core.sync.worker.SyncUpWorker
import com.vitbon.kkm.core.sync.ProductSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncMonitor: SyncMonitor,
    private val syncManager: SyncManager
) {
    fun start() {
        syncMonitor.start()
        // Периодическая синхронизация товаров
        SyncDownWorker.schedulePeriodic(context)
    }

    fun stop() {
        syncMonitor.stop()
    }

    /**
     * Вызвать после каждого фискального чека — постановка в очередь синхронизации.
     */
    fun onCheckCreated() {
        SyncUpWorker.enqueue(context)
    }

    /**
     * Синхронизировать немедленно (перед закрытием смены и т.д.)
     */
    suspend fun syncNow(): SyncResult {
        val manager = syncManager
        return manager.syncChecks()
    }

    /**
     * Принудительно выполнить pull-обновление товарного каталога.
     */
    suspend fun syncProductsNow(): ProductSyncResult {
        val manager = syncManager
        return manager.syncProducts()
    }
}
