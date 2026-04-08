package com.vitbon.kkm.features.auth.domain

import android.content.SharedPreferences
import com.vitbon.kkm.data.local.dao.CashierDao
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthUseCase @Inject constructor(
    private val cashierDao: CashierDao,
    private val prefs: SharedPreferences
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
            // Сохранить текущего кассира
            prefs.edit()
                .putString("current_cashier_id", cashier.id)
                .putString("current_cashier_name", cashier.name)
                .putString("current_cashier_role", cashier.role)
                .apply()
            AuthResult.Success(
                AuthenticatedCashier(
                    id = cashier.id,
                    name = cashier.name,
                    role = CashierRole.entries.find { it.name == cashier.role } ?: CashierRole.CASHIER
                )
            )
        } else {
            AuthResult.Error("Неверный ПИН")
        }
    }

    fun logout() {
        prefs.edit()
            .remove("current_cashier_id")
            .remove("current_cashier_name")
            .remove("current_cashier_role")
            .apply()
    }

    fun getCurrentCashierId(): String? = prefs.getString("current_cashier_id", null)
    fun getCurrentCashierName(): String? = prefs.getString("current_cashier_name", null)
    fun getCurrentCashierRole(): CashierRole? {
        val role = prefs.getString("current_cashier_role", null) ?: return null
        return CashierRole.entries.find { it.name == role }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
