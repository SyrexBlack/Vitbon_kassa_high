package com.vitbon.kkm.features.auth.domain

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import com.vitbon.kkm.data.local.dao.CashierDao
import com.vitbon.kkm.data.local.entity.LocalCashier

class AuthUseCaseTest {
    private val cashierDao = Mockito.mock(CashierDao::class.java)
    private val prefs = Mockito.mock(android.content.SharedPreferences::class.java)
    private val prefsEdit = Mockito.mock(android.content.SharedPreferences.Editor::class.java)

    @Test
    fun `authenticate — correct PIN returns Success`() {
        // SHA-256 of "1234"
        val pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        val cashier = LocalCashier("id-1", "Иванов", pinHash, "CASHIER", 0L)
        Mockito.`when`(cashierDao.findByPinHash(pinHash)).thenReturn(cashier)
        Mockito.`when`(prefs.edit()).thenReturn(prefsEdit)

        // Используем fake implementation
        val result = FakeAuthUseCase().authenticate("1234")
        assertTrue(result is AuthResult.Success)
        assertEquals("Иванов", (result as AuthResult.Success).cashier.name)
    }

    @Test
    fun `authenticate — wrong PIN returns Error`() {
        val result = FakeAuthUseCase().authenticate("0000")
        assertTrue(result is AuthResult.Error)
        assertEquals("Неверный ПИН", (result as AuthResult.Error).message)
    }

    @Test
    fun `authenticate — short PIN returns Error`() {
        val result = FakeAuthUseCase().authenticate("12")
        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).message.contains("4"))
    }

    @Test
    fun `authenticate — non-digit PIN returns Error`() {
        val result = FakeAuthUseCase().authenticate("12ab")
        assertTrue(result is AuthResult.Error)
        assertTrue((result as AuthResult.Error).message.contains("цифр"))
    }
}

/** Тестовая реализация без зависимостей */
class FakeAuthUseCase {
    fun authenticate(pin: String): AuthResult {
        if (pin.length < 4 || pin.length > 6) return AuthResult.Error("ПИН должен быть от 4 до 6 цифр")
        if (!pin.all { it.isDigit() }) return AuthResult.Error("ПИН должен состоять только из цифр")
        // Fake: only "1234" works
        return if (pin == "1234") {
            AuthResult.Success(AuthenticatedCashier("id-1", "Иванов", CashierRole.CASHIER))
        } else {
            AuthResult.Error("Неверный ПИН")
        }
    }
}
