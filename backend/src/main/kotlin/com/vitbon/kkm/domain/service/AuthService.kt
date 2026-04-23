package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.CashierDto
import com.vitbon.kkm.api.dto.LoginFeaturesDto
import com.vitbon.kkm.api.dto.LoginResponseDto
import com.vitbon.kkm.domain.persistence.AuthSessionEntity
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.service.security.AuditService
import com.vitbon.kkm.domain.service.security.SessionTokenService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AuthService(
    private val authSessionRepository: AuthSessionRepository,
    private val sessionTokenService: SessionTokenService,
    private val auditService: AuditService
) {
    fun login(pin: String, deviceId: String): LoginResponseDto {
        if (pin != DEMO_PIN) {
            auditService.write(null, null, deviceId, null, "auth.login", "cashier", "FAIL", "INVALID_PIN")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный ПИН")
        }

        val cashierId = UUID.fromString(DEMO_CASHIER_ID_UUID)

        authSessionRepository.findByCashierIdAndRevokedAtIsNull(cashierId)?.let { oldSession ->
            authSessionRepository.save(
                AuthSessionEntity(
                    id = oldSession.id,
                    cashierId = oldSession.cashierId,
                    deviceId = oldSession.deviceId,
                    tokenHash = oldSession.tokenHash,
                    issuedAt = oldSession.issuedAt,
                    expiresAt = oldSession.expiresAt,
                    revokedAt = OffsetDateTime.now(),
                    revokeReason = "REPLACED_BY_NEW_LOGIN"
                )
            )
            auditService.write(cashierId, DEMO_CASHIER_ROLE, oldSession.deviceId, oldSession.id, "auth.session.revoke", "cashier", "SUCCESS", "REPLACED_BY_NEW_LOGIN")
        }

        val token = sessionTokenService.generateOpaqueToken()
        val now = OffsetDateTime.now()
        val expiresAt = now.plusHours(8)

        val session = AuthSessionEntity(
            id = UUID.randomUUID(),
            cashierId = cashierId,
            deviceId = deviceId,
            tokenHash = sessionTokenService.sha256(token),
            issuedAt = now,
            expiresAt = expiresAt,
            revokedAt = null,
            revokeReason = null
        )
        authSessionRepository.save(session)

        auditService.write(cashierId, DEMO_CASHIER_ROLE, deviceId, session.id, "auth.login", "cashier", "SUCCESS", null)

        return LoginResponseDto(
            token = token,
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
            ),
            expiresAt = expiresAt.toInstant().toEpochMilli()
        )
    }

    fun logout(token: String) {
        val tokenHash = sessionTokenService.sha256(token)
        val session = authSessionRepository.findByTokenHash(tokenHash) ?: return
        if (session.revokedAt != null) return

        val revokedSession = AuthSessionEntity(
            id = session.id,
            cashierId = session.cashierId,
            deviceId = session.deviceId,
            tokenHash = session.tokenHash,
            issuedAt = session.issuedAt,
            expiresAt = session.expiresAt,
            revokedAt = OffsetDateTime.now(),
            revokeReason = "LOGOUT"
        )
        authSessionRepository.save(revokedSession)
        auditService.write(session.cashierId, null, session.deviceId, session.id, "auth.logout", "cashier", "SUCCESS", null)
    }

    private companion object {
        const val DEMO_PIN = "1111"
        const val DEMO_CASHIER_ID = "cashier-demo-1"
        const val DEMO_CASHIER_ID_UUID = "11111111-1111-1111-1111-111111111111"
        const val DEMO_CASHIER_NAME = "Демо Кассир"
        const val DEMO_CASHIER_ROLE = "CASHIER"
    }
}
