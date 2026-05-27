package com.vitbon.kkm.core.sync

import android.content.Context
import com.vitbon.kkm.core.sync.worker.SyncUpWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUpScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueueIfConnected() {
        SyncUpWorker.enqueueIfConnected(context)
    }
}