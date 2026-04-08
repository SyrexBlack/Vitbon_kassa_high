package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.stereotype.Service

@Service
class AuthService {
    fun login(pin: String): LoginResponseDto {
        return LoginResponseDto(
            token = "stub-token-${System.currentTimeMillis()}",
            cashier = CashierDto(
                id = UUID.randomUUID().toString(),
                name = "Тестовый Кассир",
                role = "CASHIER"
            )
        )
    }

    fun logout(token: String) {}
}
