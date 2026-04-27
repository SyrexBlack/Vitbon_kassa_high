package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.CashierDto
import com.vitbon.kkm.api.dto.LoginFeaturesDto
import com.vitbon.kkm.api.dto.LoginResponseDto
import com.vitbon.kkm.domain.persistence.AuthSessionEntity
import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierEntity
import com.vitbon.kkm.domain.persistence.CashierRepository
import com.vitbon.kkm.domain.service.security.AuditService
import com.vitbon.kkm.domain.service.security.PinHashService
import com.vitbon.kkm.domain.service.security.SessionTokenService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AuthService(
    private val authSessionRepository: AuthSessionRepository,
    private val cashierRepository: CashierRepository,
    private val sessionTokenService: SessionTokenService,
    private val pinHashService: PinHashService,
    private val auditService: AuditService
) {
    private data class FailedLoginState(
        val attempts: Int,
        val blockedUntil: OffsetDateTime?
    )

    private val failedLoginsByDevice = ConcurrentHashMap<String, FailedLoginState>()

    fun login(pin: String, deviceId: String): LoginResponseDto {
        enforceRateLimit(deviceId)

        val cashier = cashierRepository.findAll().firstOrNull { pinHashService.matches(pin, it.pinHash) }
        if (cashier == null) {
            registerFailedAttempt(deviceId)
            auditService.write(null, null, deviceId, null, "auth.login", "cashier", "FAIL", "INVALID_PIN")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный ПИН")
        }

        if (pinHashService.needsRehash(cashier.pinHash)) {
            cashierRepository.save(
                CashierEntity(
                    id = cashier.id,
                    name = cashier.name,
                    pinHash = pinHashService.hash(pin),
                    role = cashier.role,
                    createdAt = cashier.createdAt
                )
            )
        }

        clearFailedAttempts(deviceId)

        val now = OffsetDateTime.now()
        val oldSessions = authSessionRepository.findAllByCashierIdAndRevokedAtIsNull(cashier.id)
        oldSessions.forEach { oldSession ->
            authSessionRepository.save(
                AuthSessionEntity(
                    id = oldSession.id,
                    cashierId = oldSession.cashierId,
                    deviceId = oldSession.deviceId,
                    tokenHash = oldSession.tokenHash,
                    issuedAt = oldSession.issuedAt,
                    expiresAt = oldSession.expiresAt,
                    revokedAt = now,
                    revokeReason = "REPLACED_BY_NEW_LOGIN"
                )
            )
            auditService.write(cashier.id, cashier.role, oldSession.deviceId, oldSession.id, "auth.session.revoke", "cashier", "SUCCESS", "REPLACED_BY_NEW_LOGIN")
        }

        val token = sessionTokenService.generateOpaqueToken()
        val expiresAt = now.plusHours(8)

        val session = AuthSessionEntity(
            id = UUID.randomUUID(),
            cashierId = cashier.id,
            deviceId = deviceId,
            tokenHash = sessionTokenService.sha256(token),
            issuedAt = now,
            expiresAt = expiresAt,
            revokedAt = null,
            revokeReason = null
        )
        authSessionRepository.save(session)

        auditService.write(cashier.id, cashier.role, deviceId, session.id, "auth.login", "cashier", "SUCCESS", null)

        return LoginResponseDto(
            token = token,
            cashier = CashierDto(
                id = cashier.id.toString(),
                name = cashier.name,
                role = cashier.role
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

    private fun enforceRateLimit(deviceId: String) {
        val now = OffsetDateTime.now()
        val state = failedLoginsByDevice[deviceId] ?: return
        val blockedUntil = state.blockedUntil
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            auditService.write(null, null, deviceId, null, "auth.login", "cashier", "DENY", "RATE_LIMITED")
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток входа. Повторите позже")
        }
        if (blockedUntil != null && !blockedUntil.isAfter(now)) {
            failedLoginsByDevice.remove(deviceId)
        }
    }

    private fun registerFailedAttempt(deviceId: String) {
        val now = OffsetDateTime.now()
        failedLoginsByDevice.compute(deviceId) { _, existing ->
            val attempts = (existing?.attempts ?: 0) + 1
            if (attempts >= 3) {
                FailedLoginState(
                    attempts = attempts,
                    blockedUntil = now.plus(Duration.ofMinutes(5))
                )
            } else {
                FailedLoginState(
                    attempts = attempts,
                    blockedUntil = null
                )
            }
        }
    }

    private fun clearFailedAttempts(deviceId: String) {
        failedLoginsByDevice.remove(deviceId)
    }
}
