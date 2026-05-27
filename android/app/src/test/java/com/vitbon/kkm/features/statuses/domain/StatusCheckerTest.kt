package com.vitbon.kkm.features.statuses.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.features.FeatureFlag
import com.vitbon.kkm.core.features.FeatureManager
import com.vitbon.kkm.core.sync.SyncManager
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.StatusResponseDto
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import com.vitbon.kkm.features.licensing.domain.LicenseStatus as DomainLicenseStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class StatusCheckerTest {

    private val context = mockk<Context>()
    private val api = mockk<VitbonApi>()
    private val syncManager = mockk<SyncManager>()
    private val licenseChecker = mockk<LicenseChecker>()
    private val featureManager = mockk<FeatureManager>()

    private val connectivityManager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val networkCapabilities = mockk<NetworkCapabilities>()
    private val licenseStatusFlow = MutableStateFlow<DomainLicenseStatus>(DomainLicenseStatus.Active)

    private val checker = StatusChecker(context, api, syncManager, licenseChecker, featureManager)

    @Test
    fun `check maps remote statuses when online`() = runBlocking {
        mockOnlineStatus(isOnline = true)
        every { syncManager.observePendingCount() } returns flowOf(2)
        every { licenseChecker.status } returns licenseStatusFlow
        every { featureManager.isEnabledSync(any()) } returns false
        coEvery { api.getStatuses() } returns Response.success(
            StatusResponseDto(
                ofdQueueLength = 5,
                lastSyncTimestamp = 12_345L,
                cloudServerOk = false,
                licenseStatus = "GRACE_PERIOD"
            )
        )

        checker.check()

        val status = checker.status.value
        assertEquals(ConnectionStatus.AVAILABLE, status.internet)
        assertEquals(ServiceStatus.ERROR, status.cloudServer)
        assertEquals(12_345L, status.cloudLastSyncMs)
        assertEquals(5, status.ofd.pendingChecks)
        assertEquals(true, status.ofd.connected)
        assertEquals(LicenseStatus.GRACE_PERIOD, status.license)
        coVerify(exactly = 1) { api.getStatuses() }
    }

    @Test
    fun `check uses local queue and marks cloud error when offline`() = runBlocking {
        mockOnlineStatus(isOnline = false)
        every { syncManager.observePendingCount() } returns flowOf(3)
        every { licenseChecker.status } returns licenseStatusFlow
        every { featureManager.isEnabledSync(any()) } returns false

        checker.check()

        val status = checker.status.value
        assertEquals(ConnectionStatus.LOST, status.internet)
        assertEquals(ServiceStatus.ERROR, status.cloudServer)
        assertEquals(3, status.ofd.pendingChecks)
        assertEquals(false, status.ofd.connected)
        coVerify(exactly = 0) { api.getStatuses() }
    }

    @Test
    fun `check maps unlicensed status to expired and ignores zero remote sync timestamp`() = runBlocking {
        mockOnlineStatus(isOnline = true)
        every { syncManager.observePendingCount() } returns flowOf(0)
        every { licenseChecker.status } returns licenseStatusFlow
        every { featureManager.isEnabledSync(any()) } returns false
        coEvery { api.getStatuses() } returns Response.success(
            StatusResponseDto(
                ofdQueueLength = 0,
                lastSyncTimestamp = 0L,
                cloudServerOk = true,
                licenseStatus = "UNLICENSED"
            )
        )

        checker.check()

        val status = checker.status.value
        assertEquals(null, status.cloudLastSyncMs)
        assertEquals(LicenseStatus.EXPIRED, status.license)
        coVerify(exactly = 1) { api.getStatuses() }
    }

    @Test
    fun `check marks enabled optional modules unavailable when device is offline`() = runBlocking {
        mockOnlineStatus(isOnline = false)
        every { syncManager.observePendingCount() } returns flowOf(1)
        every { licenseChecker.status } returns licenseStatusFlow
        every { featureManager.isEnabledSync(FeatureFlag.EGAAIS_ENABLED) } returns true
        every { featureManager.isEnabledSync(FeatureFlag.CHASEZNAK_ENABLED) } returns true

        checker.check()

        val status = checker.status.value
        assertEquals(ModuleStatus.UNAVAILABLE, status.egaisModule)
        assertEquals(ModuleStatus.UNAVAILABLE, status.chaseznakModule)
        coVerify(exactly = 0) { api.getStatuses() }
    }

    private fun mockOnlineStatus(isOnline: Boolean) {
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        if (!isOnline) {
            every { connectivityManager.activeNetwork } returns null
            return
        }

        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
    }
}
