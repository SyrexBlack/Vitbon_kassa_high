package com.vitbon.kkm

import android.app.Application
import androidx.hilt.HiltAndroidApp
import androidx.work.Configuration
import androidx.work.WorkManager
import com.vitbon.kkm.core.sync.SyncService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VitbonApp : Application(), Configuration.Provider {

    @Inject
    lateinit var syncService: SyncService

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Запустить мониторинг сети и периодическую синхронизацию
        syncService.start()
    }
}
