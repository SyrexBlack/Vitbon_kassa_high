package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.persistence.DeviceLicenseRepository
import com.vitbon.kkm.domain.persistence.DocumentEntity
import com.vitbon.kkm.domain.persistence.DocumentItemEntity
import com.vitbon.kkm.domain.persistence.DocumentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private const val LICENSE_STATUS_ACTIVE = "ACTIVE"
private const val LICENSE_STATUS_EXPIRED = "EXPIRED"
private const val LICENSE_STATUS_GRACE_PERIOD = "GRACE_PERIOD"

@Service
class DocumentService(
    private val documentRepository: DocumentRepository
) {

    @Transactional
    fun save(doc: DocumentDto, type: String): Unit {
        if (doc.items.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "items must not be empty")
        }

        val entity = doc.toEntity(type)
        documentRepository.save(entity)
    }

    fun findDocuments(since: Long?): List<DocumentDto> {
        val entities = if (since == null) {
            documentRepository.findAll()
        } else {
            documentRepository.findByTimestampGreaterThanEqual(since.toOffsetDateTime())
        }

        return entities
            .sortedByDescending { it.timestamp }
            .map { it.toDto() }
    }

    private fun DocumentDto.toEntity(type: String): DocumentEntity {
        val documentEntity = DocumentEntity(
            id = UUID.randomUUID(),
            type = type.uppercase(),
            timestamp = timestamp.toOffsetDateTime()
        )

        val itemEntities = items.map { item ->
            DocumentItemEntity(
                id = UUID.randomUUID(),
                document = documentEntity,
                productId = item.productId?.toUUID(),
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                reason = item.reason
            )
        }
        documentEntity.items.addAll(itemEntities)
        return documentEntity
    }

    private fun DocumentEntity.toDto(): DocumentDto {
        return DocumentDto(
            type = type,
            timestamp = timestamp.toInstant().toEpochMilli(),
            items = items.map { item ->
                DocumentItemDto(
                    productId = item.productId?.toString(),
                    barcode = item.barcode,
                    name = item.name,
                    quantity = item.quantity,
                    reason = item.reason
                )
            }
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

@Service
class StatusService {
    fun getStatuses(): StatusResponseDto = StatusResponseDto(
        ofdQueueLength = 0,
        lastSyncTimestamp = System.currentTimeMillis(),
        cloudServerOk = true,
        licenseStatus = "ACTIVE"
    )
}

@Service
class LicenseService(
    private val deviceLicenseRepository: DeviceLicenseRepository
) {
    fun check(deviceId: String): LicenseCheckResponseDto {
        val row = deviceLicenseRepository.findById(deviceId).orElse(null)
            ?: return LicenseCheckResponseDto(
                status = LICENSE_STATUS_ACTIVE,
                expiryDate = null,
                graceUntil = null
            )

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val graceActive = row.graceUntil?.let { !it.isBefore(now) } == true
        val expiredByDate = row.expiryDate?.let { !it.isAfter(now) } == true

        val resolvedStatus = when {
            row.status == LICENSE_STATUS_ACTIVE && !expiredByDate -> LICENSE_STATUS_ACTIVE
            row.status != LICENSE_STATUS_ACTIVE && graceActive -> LICENSE_STATUS_GRACE_PERIOD
            else -> LICENSE_STATUS_EXPIRED
        }

        return LicenseCheckResponseDto(
            status = resolvedStatus,
            expiryDate = row.expiryDate?.toInstant()?.toEpochMilli(),
            graceUntil = row.graceUntil?.toInstant()?.toEpochMilli()
        )
    }
}

@Service
class EgaisService {
    fun processIncoming(payload: String): String = "OK"
    fun processTara(payload: String): String = "OK"
}

@Service
class ChaseznakService {
    fun processSell(payload: String): String = "OK"
    fun verifyAge(payload: String): String {
        return """{"verified": true}"""
    }
}
