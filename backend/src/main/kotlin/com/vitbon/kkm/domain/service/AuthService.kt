package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService {
    fun login(pin: String): LoginResponseDto {
        if (pin != DEMO_PIN) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный ПИН")
        }

        return LoginResponseDto(
            token = DEMO_TOKEN,
            cashier = CashierDto(
                id = DEMO_CASHIER_ID,
                name = DEMO_CASHIER_NAME,
                role = DEMO_CASHIER_ROLE
            ),
            features = LoginFeaturesDto(
                egaisEnabled = false,
                chaseznakEnabled = false,
                acquiringEnabled = true,
                sbpEnabled = true
            )
        )
    }

    fun logout(token: String) {}

    private companion object {
        const val DEMO_PIN = "1111"
        const val DEMO_TOKEN = "demo-token-1111"
        const val DEMO_CASHIER_ID = "cashier-demo-1"
        const val DEMO_CASHIER_NAME = "Демо Кассир"
        const val DEMO_CASHIER_ROLE = "CASHIER"
    }
}
