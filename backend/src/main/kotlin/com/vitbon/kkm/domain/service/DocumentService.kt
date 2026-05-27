package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.DeviceLicenseRepository
import com.vitbon.kkm.domain.persistence.DocumentEntity
import com.vitbon.kkm.domain.persistence.DocumentItemEntity
import com.vitbon.kkm.domain.persistence.DocumentRepository
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.UUID

private const val LICENSE_STATUS_ACTIVE = "ACTIVE"
private const val LICENSE_STATUS_EXPIRED = "EXPIRED"
private const val LICENSE_STATUS_GRACE_PERIOD = "GRACE_PERIOD"
private const val LICENSE_STATUS_UNLICENSED = "UNLICENSED"

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

@Service
class StatusService(
    private val checkRepository: CheckRepository,
    private val documentRepository: DocumentRepository,
    private val licenseService: LicenseService
) {
    fun getStatuses(): StatusResponseDto {
        val principal = SecurityContextHolder.requirePrincipal()
        val checks = checkRepository.findAllByCashierIdAndDeviceId(principal.cashierId, principal.deviceId)
        val documents = documentRepository.findAllByCashierIdAndDeviceId(principal.cashierId, principal.deviceId)
        val lastCheckTs = checks.maxOfOrNull { it.createdAt.toInstant().toEpochMilli() } ?: 0L
        val lastDocumentTs = documents.maxOfOrNull { it.timestamp.toInstant().toEpochMilli() } ?: 0L

        return StatusResponseDto(
            ofdQueueLength = checks.count { it.fiscalSign.isNullOrBlank() },
            lastSyncTimestamp = maxOf(lastCheckTs, lastDocumentTs),
            cloudServerOk = true,
            licenseStatus = licenseService.check(principal.deviceId).status
        )
    }
}

@Service
class LicenseService(
    private val deviceLicenseRepository: DeviceLicenseRepository
) {
    fun check(deviceId: String): LicenseCheckResponseDto {
        val row = deviceLicenseRepository.findById(deviceId).orElse(null)
            ?: return LicenseCheckResponseDto(
                status = LICENSE_STATUS_UNLICENSED,
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
class EgaisService(
    private val externalIntegrationProxyService: ExternalIntegrationProxyService,
    @Value("\${integrations.egais.incoming-url:}")
    private val incomingUrl: String,
    @Value("\${integrations.egais.tara-url:}")
    private val taraUrl: String,
    @Value("\${integrations.request-timeout-seconds:10}")
    private val requestTimeoutSeconds: Long
) {
    fun status(): EgaisStatusDto {
        return EgaisStatusDto(
            available = isEndpointReachable(incomingUrl) && isEndpointReachable(taraUrl)
        )
    }

    fun processIncoming(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = incomingUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_XML,
            integrationName = "EGAIS"
        )
    }

    fun processTara(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = taraUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_XML,
            integrationName = "EGAIS"
        )
    }

    private fun isEndpointReachable(endpointUrl: String): Boolean {
        if (endpointUrl.isBlank()) {
            return false
        }

        return runCatching {
            val uri = URI(endpointUrl)
            val host = uri.host ?: return false
            val port = when {
                uri.port != -1 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                uri.scheme.equals("http", ignoreCase = true) -> 80
                else -> return false
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), (requestTimeoutSeconds * 1000).toInt())
            }

            true
        }.getOrDefault(false)
    }
}

@Service
class ChaseznakService(
    private val externalIntegrationProxyService: ExternalIntegrationProxyService,
    @Value("\${integrations.chaseznak.sell-url:}")
    private val sellUrl: String,
    @Value("\${integrations.chaseznak.validate-url:}")
    private val validateUrl: String,
    @Value("\${integrations.chaseznak.verify-age-url:}")
    private val verifyAgeUrl: String
) {
    fun validate(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = validateUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Chestny ZNAK"
        )
    }

    fun processSell(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = sellUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Chestny ZNAK"
        )
    }

    fun verifyAge(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = verifyAgeUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Age verification"
        )
    }
}
