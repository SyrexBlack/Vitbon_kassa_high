package com.vitbon.kkm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vitbon.kkm.core.sync.worker.SyncDownWorker

/**
 * После перезагрузки устройства — перезапустить периодическую синхронизацию.
 */
class SyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncDownWorker.schedulePeriodic(context)
        }
    }
}
