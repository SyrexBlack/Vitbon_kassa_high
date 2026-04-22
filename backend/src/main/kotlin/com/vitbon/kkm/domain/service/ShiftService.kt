package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.ShiftDto
import com.vitbon.kkm.domain.persistence.ShiftEntity
import com.vitbon.kkm.domain.persistence.ShiftRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class ShiftService(
    private val shiftRepository: ShiftRepository
) {
    fun findByCashier(cashierId: String): List<ShiftDto> {
        return shiftRepository.findByCashierIdOrderByOpenedAtDesc(cashierId.toUUID())
            .map { it.toDto() }
    }

    @Transactional
    fun open(shift: ShiftDto): ShiftDto {
        val entity = shift.toEntity()
        shiftRepository.save(entity)
        return entity.toDto()
    }

    @Transactional
    fun close(id: String): Unit {
        val current = shiftRepository.findById(id.toUUID()).orElse(null) ?: return
        if (current.closedAt != null) return

        val closed = ShiftEntity(
            id = current.id,
            cashierId = current.cashierId,
            deviceId = current.deviceId,
            openedAt = current.openedAt,
            closedAt = OffsetDateTime.now(ZoneOffset.UTC),
            totalCash = current.totalCash,
            totalCard = current.totalCard
        )
        shiftRepository.save(closed)
    }

    private fun ShiftDto.toEntity(): ShiftEntity {
        return ShiftEntity(
            id = id.toUUID(),
            cashierId = cashierId.toUUID(),
            deviceId = deviceId,
            openedAt = openedAt.toOffsetDateTime(),
            closedAt = closedAt?.toOffsetDateTime(),
            totalCash = totalCash,
            totalCard = totalCard
        )
    }

    private fun ShiftEntity.toDto(): ShiftDto {
        return ShiftDto(
            id = id.toString(),
            cashierId = cashierId.toString(),
            deviceId = deviceId,
            openedAt = openedAt.toInstant().toEpochMilli(),
            closedAt = closedAt?.toInstant()?.toEpochMilli(),
            totalCash = totalCash,
            totalCard = totalCard
        )
    }

    private fun String.toUUID(): UUID {
        return runCatching { UUID.fromString(this) }
            .getOrElse { UUID.nameUUIDFromBytes(toByteArray()) }
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
    }
}
