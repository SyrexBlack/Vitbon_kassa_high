package com.vitbon.kkm.features.auth.domain

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.features.FeatureManager
import com.vitbon.kkm.data.local.dao.CashierDao
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
    private val context = mockk<Context>()
    private val featureManager = mockk<FeatureManager>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val networkCapabilities = mockk<NetworkCapabilities>()
    private val tokenStore = mockk<AuthTokenStore>(relaxed = true)
    private val emergencyAdminSessionManager = mockk<EmergencyAdminSessionManager>(relaxed = true)

    private val useCase = AuthUseCase(
        cashierDao,
        api,
        prefs,
        context,
        featureManager,
        tokenStore,
        emergencyAdminSessionManager
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
        prefs.edit().putString("device_id", "DEVICE-1").apply()
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
        coEvery { cashierDao.findByPinHash(sha256("1111")) } returns localCashier

        val result = useCase.authenticateEmergencyAdmin("1111")

        assertTrue(result is AuthResult.Error)
        assertEquals("Аварийный вход разрешён только для ADMIN", (result as AuthResult.Error).message)
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

private class InMemorySharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        val value = data[key]
        return if (value is String?) value else defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = data[key]
        return if (value is MutableSet<*>) value as MutableSet<String> else defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
    }

    private class Editor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                data.clear()
                clearRequested = false
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    data.remove(key)
                } else {
                    data[key] = value
                }
            }
            pending.clear()
        }
    }
}
