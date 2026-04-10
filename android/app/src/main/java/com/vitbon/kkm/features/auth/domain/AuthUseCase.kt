package com.vitbon.kkm.features.auth.domain

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    @ApplicationContext private val context: Context
) {
    suspend fun authenticate(pin: String): AuthResult {
        if (pin.length < 4 || pin.length > 6) {
            return AuthResult.Error("ПИН должен быть от 4 до 6 цифр")
        }
        if (!pin.all { it.isDigit() }) {
            return AuthResult.Error("ПИН должен состоять только из цифр")
        }

        val pinHash = sha256(pin)
        val cashier = cashierDao.findByPinHash(pinHash)

        return if (cashier != null) {
            prefs.edit()
                .putString("current_cashier_id", cashier.id)
                .putString("current_cashier_name", cashier.name)
                .putString("current_cashier_role", cashier.role)
                .apply()

            AuthResult.Success(
                cashier = AuthenticatedCashier(
                    id = cashier.id,
                    name = cashier.name,
                    role = CashierRole.entries.find { it.name == cashier.role } ?: CashierRole.CASHIER
                )
            )
        } else {
            AuthResult.Error("Неверный ПИН")
        }
    }

    suspend fun validateWithBackendBestEffort(pin: String): String? {
        if (!isOnline()) return null

        return try {
            val response = api.login(LoginRequestDto(pin))
            if (response.isSuccessful) {
                val body = response.body()
                prefs.edit()
                    .putLong("last_backend_auth_ts", System.currentTimeMillis())
                    .putBoolean("last_backend_auth_ok", true)
                    .putString("auth_token", body?.token)
                    .remove("backend_auth_warning")
                    .apply()
                null
            } else {
                val warning = "Локальный вход выполнен, но сервер не подтвердил авторизацию"
                prefs.edit()
                    .putLong("last_backend_auth_ts", System.currentTimeMillis())
                    .putBoolean("last_backend_auth_ok", false)
                    .putString("backend_auth_warning", warning)
                    .apply()
                warning
            }
        } catch (e: Exception) {
            val warning = "Локальный вход выполнен, сервер временно недоступен"
            prefs.edit()
                .putLong("last_backend_auth_ts", System.currentTimeMillis())
                .putBoolean("last_backend_auth_ok", false)
                .putString("backend_auth_warning", warning)
                .apply()
            warning
        }
    }

    fun logout() {
        prefs.edit()
            .remove("current_cashier_id")
            .remove("current_cashier_name")
            .remove("current_cashier_role")
            .remove("auth_token")
            .apply()
    }

    fun getCurrentCashierId(): String? = prefs.getString("current_cashier_id", null)
    fun getCurrentCashierName(): String? = prefs.getString("current_cashier_name", null)
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
}
