package com.vitbon.kkm.features.auth.domain

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.features.FeatureManager
import com.vitbon.kkm.core.sync.LocalAuditBufferRepository
import com.vitbon.kkm.core.sync.SyncPrefs
import com.vitbon.kkm.core.sync.SyncUpScheduler
import com.vitbon.kkm.data.local.dao.CashierDao
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import com.vitbon.kkm.data.local.entity.LocalCashier
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.CashierDto
import com.vitbon.kkm.data.remote.dto.LoginFeaturesDto
import com.vitbon.kkm.data.remote.dto.LoginRequestDto
import com.vitbon.kkm.data.remote.dto.LoginResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AuthUseCaseTest {
    private val cashierDao = mockk<CashierDao>()
    private val api = mockk<VitbonApi>()
    private val prefs = InMemorySharedPreferences()
    private val securePrefs = InMemorySharedPreferences()
    private val syncPrefs = SyncPrefs(prefs, securePrefs)
    private val context = mockk<Context>()
    private val featureManager = mockk<FeatureManager>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val networkCapabilities = mockk<NetworkCapabilities>()
    private val tokenStore = mockk<AuthTokenStore>(relaxed = true)
    private val emergencyAdminSessionManager = mockk<EmergencyAdminSessionManager>(relaxed = true)
    private val localAuditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
    private val syncUpScheduler = mockk<SyncUpScheduler>(relaxed = true)

    private val useCase = AuthUseCase(
        cashierDao,
        api,
        prefs,
        syncPrefs,
        context,
        featureManager,
        tokenStore,
        emergencyAdminSessionManager,
        localAuditBufferRepository,
        syncUpScheduler
    )

    @Test
    fun `authenticate fails when backend unavailable even if local cashier exists`() = runBlocking {
        val pin = "1111"
        val localCashier = LocalCashier(
            id = "cashier-1",
            name = "Иванов",
            pinHash = "hash-1111",
            role = "CASHIER",
            createdAt = 1L
        )
        mockOnlineStatus(isOnline = false)
        coEvery { cashierDao.findByPinHash(any()) } returns localCashier

        val result = useCase.authenticate(pin)

        assertTrue(result is AuthResult.Error)
        assertEquals("Требуется подключение к серверу для входа", (result as AuthResult.Error).message)
    }

    @Test
    fun `authenticate stores token via token store on success`() = runBlocking {
        val pin = "1111"
        val features = LoginFeaturesDto(
            egaisEnabled = true,
            chaseznakEnabled = false,
            acquiringEnabled = true,
            sbpEnabled = true
        )
        mockOnlineStatus(isOnline = true)
        securePrefs.edit().putString("device_id", "DEVICE-1").apply()
        coEvery { api.login(LoginRequestDto(pin = "1111", deviceId = "DEVICE-1")) } returns Response.success(
            LoginResponseDto(
                token = "opaque-token",
                cashier = CashierDto(id = "cashier-1", name = "Иванов", role = "CASHIER"),
                features = features,
                expiresAt = System.currentTimeMillis() + 60_000
            )
        )

        val result = useCase.authenticate(pin)

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals("cashier-1", success.cashier.id)
        assertEquals("Иванов", success.cashier.name)
        assertEquals(CashierRole.CASHIER, success.cashier.role)
        verify(exactly = 1) { tokenStore.save("opaque-token") }
        verify(exactly = 1) { featureManager.applyFeatures(features) }
        verify(exactly = 1) { emergencyAdminSessionManager.clear() }
        assertEquals("cashier-1", prefs.getString("current_cashier_id", null))
        assertEquals("Иванов", prefs.getString("current_cashier_name", null))
        assertEquals("CASHIER", prefs.getString("current_cashier_role", null))
    }

    @Test
    fun `authenticate returns Error for short pin`() = runBlocking {
        val result = useCase.authenticate("12")

        assertTrue(result is AuthResult.Error)
        assertEquals("ПИН должен быть от 4 до 6 цифр", (result as AuthResult.Error).message)
    }

    @Test
    fun `authenticate returns Error for non-digit pin`() = runBlocking {
        val result = useCase.authenticate("12ab")

        assertTrue(result is AuthResult.Error)
        assertEquals("ПИН должен состоять только из цифр", (result as AuthResult.Error).message)
    }

    @Test
    fun `authenticateEmergencyAdmin activates emergency session for local admin`() = runBlocking {
        val localAdmin = LocalCashier(
            id = "admin-1",
            name = "Админ",
            pinHash = sha256("1111"),
            role = "ADMIN",
            createdAt = 1L
        )
        mockOnlineStatus(isOnline = false)
        coEvery { cashierDao.findByPinHash(sha256("1111")) } returns localAdmin

        val result = useCase.authenticateEmergencyAdmin("1111")

        assertTrue(result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals(CashierRole.ADMIN, success.cashier.role)
        assertEquals("admin-1", success.cashier.id)
        verify(exactly = 1) { emergencyAdminSessionManager.activate("admin-1") }
        verify(exactly = 1) { tokenStore.clear() }
        assertEquals("admin-1", prefs.getString("current_cashier_id", null))
        assertEquals("Админ", prefs.getString("current_cashier_name", null))
        assertEquals("ADMIN", prefs.getString("current_cashier_role", null))
    }

    @Test
    fun `authenticateEmergencyAdmin rejects non-admin local role`() = runBlocking {
        val localCashier = LocalCashier(
            id = "cashier-1",
            name = "Кассир",
            pinHash = sha256("1111"),
            role = "CASHIER",
            createdAt = 1L
        )
        mockOnlineStatus(isOnline = false)
        coEvery { cashierDao.findByPinHash(sha256("1111")) } returns localCashier

        val result = useCase.authenticateEmergencyAdmin("1111")

        assertTrue(result is AuthResult.Error)
        assertEquals("Аварийный вход разрешён только для ADMIN", (result as AuthResult.Error).message)
    }

    @Test
    fun `authenticateEmergencyAdmin rejects when backend is reachable`() = runBlocking {
        mockOnlineStatus(isOnline = true)

        val result = useCase.authenticateEmergencyAdmin("1111")

        assertTrue(result is AuthResult.Error)
        assertEquals("Аварийный вход доступен только при недоступности сервера", (result as AuthResult.Error).message)
    }

    @Test
    fun `isEmergencySessionActive clears stale admin context when emergency session expired`() = runBlocking {
        every { emergencyAdminSessionManager.isActive() } returns false
        every { tokenStore.read() } returns null
        prefs.edit()
            .putString("current_cashier_id", "admin-1")
            .putString("current_cashier_name", "Админ")
            .putString("current_cashier_role", "ADMIN")
            .apply()

        val active = useCase.isEmergencySessionActive()

        assertEquals(false, active)
        assertNull(prefs.getString("current_cashier_id", null))
        assertNull(prefs.getString("current_cashier_name", null))
        assertNull(prefs.getString("current_cashier_role", null))
    }

    @Test
    fun `auditEmergencyOperationDenied enqueues transport audit and triggers sync`() = runBlocking {
        prefs.edit().putString("current_cashier_id", "admin-1").apply()
        securePrefs.edit().putString("device_id", "DEVICE-1").apply()

        useCase.auditEmergencyOperationDenied("SALE")

        coVerify(exactly = 1) {
            localAuditBufferRepository.enqueue(
                cashierId = "admin-1",
                deviceId = "DEVICE-1",
                action = "auth.emergency.operation_denied",
                details = "DENY:SALE",
                timestamp = any()
            )
        }
        verify(exactly = 1) { syncUpScheduler.enqueueIfConnected() }
    }

    private fun mockOnlineStatus(isOnline: Boolean) {
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns isOnline
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

}
