package com.vitbon.kkm.domain.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "cashiers")
class CashierEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "pin_hash", nullable = false)
    val pinHash: String,

    @Column(name = "role", nullable = false)
    val role: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime
)

@Entity
@Table(name = "auth_sessions")
class AuthSessionEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "cashier_id", nullable = false)
    val cashierId: UUID,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: OffsetDateTime,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: OffsetDateTime,

    @Column(name = "revoked_at")
    val revokedAt: OffsetDateTime?,

    @Column(name = "revoke_reason")
    val revokeReason: String?
)

@Entity
@Table(name = "audit_events")
class AuditEventEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "actor_id")
    val actorId: UUID?,

    @Column(name = "actor_role")
    val actorRole: String?,

    @Column(name = "device_id")
    val deviceId: String?,

    @Column(name = "session_id")
    val sessionId: UUID?,

    @Column(name = "action", nullable = false)
    val action: String,

    @Column(name = "target")
    val target: String?,

    @Column(name = "result", nullable = false)
    val result: String,

    @Column(name = "reason")
    val reason: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime
)
