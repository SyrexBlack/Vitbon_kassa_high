package com.vitbon.kkm.domain.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "shifts")
class ShiftEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "cashier_id", nullable = false)
    val cashierId: UUID,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "opened_at", nullable = false)
    val openedAt: OffsetDateTime,

    @Column(name = "closed_at")
    val closedAt: OffsetDateTime?,

    @Column(name = "total_cash")
    val totalCash: Long,

    @Column(name = "total_card")
    val totalCard: Long
)
