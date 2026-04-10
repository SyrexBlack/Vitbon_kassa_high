package com.vitbon.kkm.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.vitbon.kkm.core.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncManager.syncChecks()
        return if (result.failed == 0) {
            Result.success()
        } else if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sync_up"
        private const val MAX_RETRIES = 5

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncUpWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun enqueueIfConnected(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncUpWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
        }
    }
}