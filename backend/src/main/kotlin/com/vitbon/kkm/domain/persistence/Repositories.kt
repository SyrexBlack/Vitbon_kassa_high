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
