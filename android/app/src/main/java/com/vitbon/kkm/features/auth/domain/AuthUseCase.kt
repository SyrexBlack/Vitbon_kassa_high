package com.vitbon.kkm.features.auth.domain

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vitbon.kkm.core.features.FeatureManager
import com.vitbon.kkm.core.sync.LocalAuditBufferRepository
import com.vitbon.kkm.data.local.dao.CashierDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.LoginRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthUseCase @Inject constructor(
    private val cashierDao: CashierDao,
    private val api: VitbonApi,
    private val prefs: SharedPreferences,
    @ApplicationContext private val context: Context,
    private val featureManager: FeatureManager,
    private val tokenStore: AuthTokenStore,
    private val emergencyAdminSessionManager: EmergencyAdminSessionManager,
    private val localAuditBufferRepository: LocalAuditBufferRepository
) {
    suspend fun authenticate(pin: String): AuthResult {
        if (pin.length < 4 || pin.length > 6) {
            return AuthResult.Error("ПИН должен быть от 4 до 6 цифр")
        }
        if (!pin.all { it.isDigit() }) {
            return AuthResult.Error("ПИН должен состоять только из цифр")
        }
        if (!isOnline()) {
            return AuthResult.Error("Требуется подключение к серверу для входа")
        }

        val deviceId = prefs.getString("device_id", null) ?: "UNKNOWN_DEVICE"
        val response = try {
            api.login(LoginRequestDto(pin = pin, deviceId = deviceId))
        } catch (e: Exception) {
            return AuthResult.Error("Сервер авторизации недоступен")
        }

        if (!response.isSuccessful || response.body() == null) {
            return AuthResult.Error("Неверный ПИН")
        }

        val body = response.body()!!
        tokenStore.save(body.token)
        featureManager.applyFeatures(body.features)

        val emergencyWasActive = emergencyAdminSessionManager.isActive()
        emergencyAdminSessionManager.clear()
        if (emergencyWasActive) {
            enqueueAudit("auth.emergency.exit", "SUCCESS")
        }

        prefs.edit()
            .putString("current_cashier_id", body.cashier.id)
            .putString("current_cashier_name", body.cashier.name)
            .putString("current_cashier_role", body.cashier.role)
            .apply()

        return AuthResult.Success(
            cashier = AuthenticatedCashier(
                id = body.cashier.id,
                name = body.cashier.name,
                role = CashierRole.entries.find { it.name == body.cashier.role } ?: CashierRole.CASHIER
            )
        )
    }

    suspend fun authenticateEmergencyAdmin(pin: String): AuthResult {
        if (pin.length < 4 || pin.length > 6) {
            return AuthResult.Error("ПИН должен быть от 4 до 6 цифр")
        }
        if (!pin.all { it.isDigit() }) {
            return AuthResult.Error("ПИН должен состоять только из цифр")
        }
        if (isOnline()) {
            enqueueAudit("auth.emergency.enter", "DENY:BACKEND_AVAILABLE")
            return AuthResult.Error("Аварийный вход доступен только при недоступности сервера")
        }

        val localCashier = cashierDao.findByPinHash(sha256(pin))
            ?: return AuthResult.Error("Неверный ПИН")

        val role = CashierRole.entries.find { it.name == localCashier.role } ?: CashierRole.CASHIER
        if (role != CashierRole.ADMIN) {
            enqueueAudit("auth.emergency.enter", "DENY:ROLE_FORBIDDEN")
            return AuthResult.Error("Аварийный вход разрешён только для ADMIN")
        }

        tokenStore.clear()
        prefs.edit()
            .putString("current_cashier_id", localCashier.id)
            .putString("current_cashier_name", localCashier.name)
            .putString("current_cashier_role", CashierRole.ADMIN.name)
            .apply()

        emergencyAdminSessionManager.activate(localCashier.id)
        enqueueAudit("auth.emergency.enter", "SUCCESS")

        return AuthResult.Success(
            cashier = AuthenticatedCashier(
                id = localCashier.id,
                name = localCashier.name,
                role = CashierRole.ADMIN
            )
        )
    }

    fun logout() {
        val emergencyWasActive = emergencyAdminSessionManager.isActive()
        tokenStore.clear()
        emergencyAdminSessionManager.clear()
        prefs.edit()
            .remove("current_cashier_id")
            .remove("current_cashier_name")
            .remove("current_cashier_role")
            .apply()

        if (emergencyWasActive) {
            enqueueAudit("auth.emergency.exit", "SUCCESS")
        }
    }

    fun getCurrentCashierId(): String? = prefs.getString("current_cashier_id", null)
    fun getCurrentCashierName(): String? = prefs.getString("current_cashier_name", null)
    fun isEmergencySessionActive(): Boolean {
        val active = emergencyAdminSessionManager.isActive()
        if (!active) {
            val role = prefs.getString("current_cashier_role", null)
            if (role == CashierRole.ADMIN.name && tokenStore.read() == null) {
                prefs.edit()
                    .remove("current_cashier_id")
                    .remove("current_cashier_name")
                    .remove("current_cashier_role")
                    .apply()
                enqueueAudit("auth.emergency.exit", "SUCCESS")
            }
        }
        return active
    }
    fun getCurrentCashierRole(): CashierRole? {
        val role = prefs.getString("current_cashier_role", null) ?: return null
        return CashierRole.entries.find { it.name == role }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun enqueueAudit(action: String, details: String?) {
        val cashierId = prefs.getString("current_cashier_id", null)
        val deviceId = prefs.getString("device_id", null)
        kotlinx.coroutines.runBlocking {
            localAuditBufferRepository.enqueue(
                cashierId = cashierId,
                deviceId = deviceId,
                action = action,
                details = details
            )
        }
    }
}
