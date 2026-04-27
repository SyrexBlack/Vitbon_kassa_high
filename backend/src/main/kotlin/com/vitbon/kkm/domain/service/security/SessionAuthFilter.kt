package com.vitbon.kkm.domain.service.security

import com.vitbon.kkm.domain.persistence.AuthSessionRepository
import com.vitbon.kkm.domain.persistence.CashierRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

@Component
class SessionAuthFilter(
    private val authSessionRepository: AuthSessionRepository,
    private val cashierRepository: CashierRepository,
    private val sessionTokenService: SessionTokenService,
    private val routeAccessPolicy: RouteAccessPolicy,
    private val auditService: AuditService
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requiredRoles = routeAccessPolicy.requiredRoles(request.method, request.requestURI)
        if (requiredRoles.isEmpty()) {
            chain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            auditService.write(
                actorId = null,
                actorRole = null,
                deviceId = request.getHeader("X-Device-Id"),
                sessionId = null,
                action = "security.auth_deny",
                target = request.requestURI,
                result = "DENY",
                reason = "MISSING_BEARER"
            )
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token")
            return
        }

        val rawToken = authHeader.removePrefix("Bearer ").trim()
        if (rawToken.isBlank()) {
            auditService.write(
                actorId = null,
                actorRole = null,
                deviceId = request.getHeader("X-Device-Id"),
                sessionId = null,
                action = "security.auth_deny",
                target = request.requestURI,
                result = "DENY",
                reason = "MISSING_BEARER"
            )
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token")
            return
        }

        val session = authSessionRepository.findByTokenHash(sessionTokenService.sha256(rawToken))
        if (session == null || session.revokedAt != null || session.expiresAt.isBefore(OffsetDateTime.now())) {
            auditService.write(
                actorId = null,
                actorRole = null,
                deviceId = request.getHeader("X-Device-Id"),
                sessionId = null,
                action = "security.auth_deny",
                target = request.requestURI,
                result = "DENY",
                reason = "INVALID_SESSION"
            )
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid session")
            return
        }

        val requestDeviceId = request.getHeader("X-Device-Id")?.trim()
        if (requestDeviceId.isNullOrBlank() || requestDeviceId != session.deviceId) {
            auditService.write(
                actorId = session.cashierId,
                actorRole = null,
                deviceId = requestDeviceId,
                sessionId = session.id,
                action = "security.auth_deny",
                target = request.requestURI,
                result = "DENY",
                reason = "DEVICE_MISMATCH"
            )
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid device binding")
            return
        }

        val role = cashierRepository.findById(session.cashierId)
            .map { it.role }
            .orElse(null)

        if (role == null || !requiredRoles.contains(role)) {
            auditService.write(
                actorId = session.cashierId,
                actorRole = role,
                deviceId = session.deviceId,
                sessionId = session.id,
                action = "security.route_deny",
                target = request.requestURI,
                result = "DENY",
                reason = "ROLE_FORBIDDEN"
            )
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
            return
        }

        auditService.write(
            actorId = session.cashierId,
            actorRole = role,
            deviceId = session.deviceId,
            sessionId = session.id,
            action = "security.route_access",
            target = request.requestURI,
            result = "ALLOW",
            reason = null
        )

        chain.doFilter(request, response)
    }
}
