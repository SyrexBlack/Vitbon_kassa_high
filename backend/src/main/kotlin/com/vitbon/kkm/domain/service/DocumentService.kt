package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.DocumentDto
import com.vitbon.kkm.api.dto.DocumentItemDto
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

@Service
class DocumentService(
    private val documentRepository: DocumentRepository
) {

    @Transactional
    fun save(doc: DocumentDto, type: String, cashierId: String, deviceId: String): Unit {
        if (doc.items.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "items must not be empty")
        }

        val entity = doc.toEntity(type, cashierId, deviceId)
        documentRepository.save(entity)
    }

    fun findDocuments(since: Long?, cashierId: String, deviceId: String): List<DocumentDto> {
        val entities = if (since == null) {
            documentRepository.findAllByCashierIdAndDeviceId(cashierId.toUUID(), deviceId)
        } else {
            documentRepository.findByCashierIdAndDeviceIdAndTimestampGreaterThanEqual(
                cashierId = cashierId.toUUID(),
                deviceId = deviceId,
                since = since.toOffsetDateTime()
            )
        }

        return entities
            .sortedByDescending { it.timestamp }
            .map { it.toDto() }
    }

    private fun DocumentDto.toEntity(type: String, cashierId: String, deviceId: String): DocumentEntity {
        val documentEntity = DocumentEntity(
            id = UUID.randomUUID(),
            type = type.uppercase(),
            cashierId = cashierId.toUUID(),
            deviceId = deviceId,
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
