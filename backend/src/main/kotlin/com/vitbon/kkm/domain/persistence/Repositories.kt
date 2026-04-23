package com.vitbon.kkm.domain.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.UUID

interface CheckRepository : JpaRepository<CheckEntity, UUID> {
    fun findByShiftId(shiftId: UUID): List<CheckEntity>
    fun findByCreatedAtGreaterThanEqual(since: OffsetDateTime): List<CheckEntity>
    fun findByShiftIdAndCreatedAtGreaterThanEqual(shiftId: UUID, since: OffsetDateTime): List<CheckEntity>
}

interface DocumentRepository : JpaRepository<DocumentEntity, UUID> {
    fun findByTimestampGreaterThanEqual(since: OffsetDateTime): List<DocumentEntity>
}

interface ShiftRepository : JpaRepository<ShiftEntity, UUID> {
    fun findByCashierIdOrderByOpenedAtDesc(cashierId: UUID): List<ShiftEntity>
}

interface ProductRepository : JpaRepository<ProductEntity, UUID> {
    fun findByUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(updatedAt: OffsetDateTime): List<ProductEntity>
    fun findAllByOrderByUpdatedAtAsc(): List<ProductEntity>
}

interface ProductDeletionRepository : JpaRepository<ProductDeletionEntity, UUID> {
    fun findByDeletedAtGreaterThanEqualOrderByDeletedAtAsc(deletedAt: OffsetDateTime): List<ProductDeletionEntity>
}

interface DeviceLicenseRepository : JpaRepository<DeviceLicenseEntity, String>

interface AuthSessionRepository : JpaRepository<AuthSessionEntity, UUID> {
    fun findByTokenHash(tokenHash: String): AuthSessionEntity?
    fun findByCashierIdAndRevokedAtIsNull(cashierId: UUID): AuthSessionEntity?
}

interface AuditEventRepository : JpaRepository<AuditEventEntity, UUID>
