package com.vitbon.kkm.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.vitbon.kkm.core.sync.worker.SyncDownWorker
import com.vitbon.kkm.core.sync.worker.SyncUpWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncMonitor @Inject constructor(
    private val context: Context
) {
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            // При восстановлении связи — синхронизировать накопленные чеки
            SyncUpWorker.enqueueIfConnected(context)
            SyncDownWorker.triggerNow(context)
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }
    }

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, connectivityCallback)

        // Начальное состояние
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun stop() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.unregisterNetworkCallback(connectivityCallback)
    }
}
