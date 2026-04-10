package com.vitbon.kkm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vitbon.kkm.core.sync.SyncService
import com.vitbon.kkm.features.bootstrap.domain.SeedDataUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VitbonApp : Application(), Configuration.Provider {

    @Inject
    lateinit var syncService: SyncService

    @Inject
    lateinit var seedDataUseCase: SeedDataUseCase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seedDataUseCase.seedIfNeeded()
        }
        // Запустить мониторинг сети и периодическую синхронизацию
        syncService.start()
    }
}
