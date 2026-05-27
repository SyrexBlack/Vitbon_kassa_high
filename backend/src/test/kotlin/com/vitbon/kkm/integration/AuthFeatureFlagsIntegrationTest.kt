package com.vitbon.kkm.integration

import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.service.AuthService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    properties = [
        "features.egais-enabled=true",
        "features.chaseznak-enabled=true",
        "features.acquiring-enabled=false",
        "features.sbp-enabled=false"
    ]
)
class AuthFeatureFlagsIntegrationTest {

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var cashierRepository: CashierRepository

    @Autowired
    lateinit var authSessionRepository: AuthSessionRepository

    @BeforeEach
    fun setUpCashiers() {
        authSessionRepository.deleteAll()
        cashierRepository.deleteAll()
        cashierRepository.save(
            CashierEntity(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                name = "Демо Кассир",
                pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                role = "CASHIER",
                createdAt = OffsetDateTime.now()
            )
        )
    }

    @Test
    fun `login response reflects configured feature flags`() {
        val login = authService.login("1234", "DEVICE-FEATURE-FLAGS")

        assertTrue(login.features.egaisEnabled)
        assertTrue(login.features.chaseznakEnabled)
        assertFalse(login.features.acquiringEnabled)
        assertFalse(login.features.sbpEnabled)
    }
}