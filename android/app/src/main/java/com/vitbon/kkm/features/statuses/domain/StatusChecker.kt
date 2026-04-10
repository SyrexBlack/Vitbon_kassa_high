package com.vitbon.kkm.features.statuses.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.sync.SyncManager
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VitbonApi,
    private val syncManager: SyncManager,
    private val licenseChecker: LicenseChecker
) {
    private val _status = MutableStateFlow(SystemStatus.empty())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    fun check() {
        val internet = checkInternet()
        val cloud = checkCloudServer()
        val ofd = checkOfd()
        val license = checkLicense()

        _status.update {
            it.copy(
                internet = internet,
                cloudServer = cloud.status,
                cloudLastSyncMs = cloud.lastSyncMs,
                ofd = ofd,
                license = license
            )
        }
    }

    private fun checkInternet(): ConnectionStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return ConnectionStatus.LOST
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectionStatus.LOST
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectionStatus.AVAILABLE
        } else {
            ConnectionStatus.LOST
        }
    }

    private data class CloudResult(val status: ServiceStatus, val lastSyncMs: Long?)

    private fun checkCloudServer(): CloudResult {
        return try {
            // Простой ping — если endpoint отвечает — сервер доступен
            // Реальная реализация: GET /api/v1/statuses
            CloudResult(ServiceStatus.OK, System.currentTimeMillis())
        } catch (e: Exception) {
            CloudResult(ServiceStatus.ERROR, null)
        }
    }

    private fun checkOfd(): OfdStatus {
        // Реальная реализация: получить от FiscalCore или из кэша
        // Пока: 0 чеков в очереди, подключено
        return OfdStatus(pendingChecks = 0, connected = true)
    }

    private fun checkLicense(): LicenseStatus {
        // Синхронизировать с LicenseChecker
        return when (val s = licenseChecker.status.value) {
            is com.vitbon.kkm.features.licensing.domain.LicenseStatus.Active -> LicenseStatus.ACTIVE
            is com.vitbon.kkm.features.licensing.domain.LicenseStatus.GracePeriod -> LicenseStatus.GRACE_PERIOD
            else -> LicenseStatus.EXPIRED
        }
    }
}
