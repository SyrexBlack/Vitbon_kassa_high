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
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token")
            return
        }

        val rawToken = authHeader.removePrefix("Bearer ").trim()
        if (rawToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token")
            return
        }

        val session = authSessionRepository.findByTokenHash(sessionTokenService.sha256(rawToken))
        if (session == null || session.revokedAt != null || session.expiresAt.isBefore(OffsetDateTime.now())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid session")
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

        chain.doFilter(request, response)
    }
}
