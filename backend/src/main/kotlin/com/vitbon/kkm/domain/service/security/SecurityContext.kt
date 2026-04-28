package com.vitbon.kkm.domain.service.security

import java.util.UUID

data class AuthPrincipal(
    val cashierId: UUID,
    val role: String,
    val deviceId: String,
    val sessionId: UUID
)
