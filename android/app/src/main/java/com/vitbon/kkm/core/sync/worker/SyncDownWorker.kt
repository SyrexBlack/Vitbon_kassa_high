package com.vitbon.kkm.core.sync.worker

import android.content.Context
import androidx.work.*
import com.vitbon.kkm.core.sync.SyncManager
import java.util.concurrent.TimeUnit

class SyncDownWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncManager = SyncManager(/* injected via Hilt */)
        val result = syncManager.syncProducts()
        return if (result.received > 0 || result.deleted > 0) {
            Result.success()
        } else {
            Result.success() // ok even if nothing changed
        }
    }

    companion object {
        const val WORK_NAME = "sync_down"
        const val INTERVAL_MINUTES = 1L // 30 sec spec, use 1 min for battery

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncDownWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        /** Trigger immediate sync when connectivity is restored */
        fun triggerNow(context: Context) {
            enqueueOneTime(context)
        }

        private fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncDownWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }
    }
}
