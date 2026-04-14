package com.vitbon.kkm.features.statuses.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.features.FeatureFlag
import com.vitbon.kkm.core.features.FeatureManager
import com.vitbon.kkm.core.sync.SyncManager
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import com.vitbon.kkm.features.licensing.domain.LicenseStatus as DomainLicenseStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VitbonApi,
    private val syncManager: SyncManager,
    private val licenseChecker: LicenseChecker,
    private val featureManager: FeatureManager
) {
    private val _status = MutableStateFlow(SystemStatus.empty())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()

    suspend fun check() {
        val internet = checkInternet()
        val pendingChecks = syncManager.observePendingCount().first()

        val remoteBody = if (internet == ConnectionStatus.AVAILABLE) {
            runCatching { api.getStatuses() }
                .getOrNull()
                ?.takeIf { it.isSuccessful }
                ?.body()
        } else {
            null
        }

        val cloudServer = when {
            internet != ConnectionStatus.AVAILABLE -> ServiceStatus.ERROR
            remoteBody?.cloudServerOk == true -> ServiceStatus.OK
            else -> ServiceStatus.ERROR
        }

        val ofdQueueLength = (remoteBody?.ofdQueueLength ?: pendingChecks).coerceAtLeast(0)
        val ofdStatus = OfdStatus(
            pendingChecks = ofdQueueLength,
            connected = internet == ConnectionStatus.AVAILABLE && remoteBody != null
        )

        val egaisModule = if (featureManager.isEnabledSync(FeatureFlag.EGAAIS_ENABLED)) {
            ModuleStatus.ACTIVE
        } else {
            ModuleStatus.INACTIVE
        }

        val chaseznakModule = if (featureManager.isEnabledSync(FeatureFlag.CHASEZNAK_ENABLED)) {
            ModuleStatus.ACTIVE
        } else {
            ModuleStatus.INACTIVE
        }

        _status.update {
            it.copy(
                internet = internet,
                cloudServer = cloudServer,
                cloudLastSyncMs = remoteBody?.lastSyncTimestamp,
                ofd = ofdStatus,
                chaseznakModule = chaseznakModule,
                egaisModule = egaisModule,
                license = resolveLicense(remoteBody?.licenseStatus)
            )
        }
    }

    private fun resolveLicense(remoteStatus: String?): LicenseStatus {
        return when (remoteStatus?.uppercase()) {
            "ACTIVE" -> LicenseStatus.ACTIVE
            "GRACE_PERIOD" -> LicenseStatus.GRACE_PERIOD
            "EXPIRED" -> LicenseStatus.EXPIRED
            else -> mapDomainLicense(licenseChecker.status.value)
        }
    }

    private fun mapDomainLicense(domainStatus: DomainLicenseStatus): LicenseStatus {
        return when (domainStatus) {
            is DomainLicenseStatus.Active -> LicenseStatus.ACTIVE
            is DomainLicenseStatus.GracePeriod -> LicenseStatus.GRACE_PERIOD
            else -> LicenseStatus.EXPIRED
        }
    }

    private fun checkInternet(): ConnectionStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ConnectionStatus.LOST
        val network = cm.activeNetwork ?: return ConnectionStatus.LOST
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectionStatus.LOST
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && validated) {
            ConnectionStatus.AVAILABLE
        } else {
            ConnectionStatus.LOST
        }
    }
}
