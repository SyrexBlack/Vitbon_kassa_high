package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.persistence.CheckEntity
import com.vitbon.kkm.domain.persistence.CheckItemEntity
import com.vitbon.kkm.domain.persistence.CheckRepository
import com.vitbon.kkm.domain.persistence.ShiftRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class CheckService(
    private val checkRepository: CheckRepository,
    private val shiftRepository: ShiftRepository,
    private val transactionTemplate: TransactionTemplate
) {

    fun processSync(checks: List<CheckDto>, cashierId: String, deviceId: String): CheckSyncResponseDto {
        val failed = mutableListOf<FailedCheckDto>()
        var processed = 0

        checks.forEach { dto ->
            try {
                val persisted = transactionTemplate.execute {
                    validateShiftOwnership(dto.shiftId, cashierId, deviceId)

                    val existing = checkRepository.findByLocalUuid(dto.localUuid)
                    when {
                        existing == null -> {
                            checkRepository.save(
                                dto.toEntity(
                                    cashierId = cashierId,
                                    deviceId = deviceId
                                )
                            )
                            true
                        }
                        existing.belongsTo(cashierId, deviceId) -> false
                        else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
                    }
                } ?: false

                if (persisted || checkRepository.findByLocalUuid(dto.localUuid)?.belongsTo(cashierId, deviceId) == true) {
                    processed += 1
                }
            } catch (exception: ResponseStatusException) {
                failed += FailedCheckDto(
                    localUuid = dto.localUuid,
                    error = exception.reason ?: exception.statusCode.toString()
                )
            } catch (exception: Exception) {
                failed += FailedCheckDto(
                    localUuid = dto.localUuid,
                    error = exception.message ?: "SYNC_FAILED"
                )
            }
        }

        return CheckSyncResponseDto(processed, failed)
    }

    @Transactional
    fun create(check: CheckDto, cashierId: String, deviceId: String): CheckDto {
        validateShiftOwnership(check.shiftId, cashierId, deviceId)
        val saved = checkRepository.save(
            check.toEntity(
                cashierId = cashierId,
                deviceId = deviceId
            )
        )
        return saved.toDto()
    }

    fun findById(id: String, cashierId: String, deviceId: String): CheckDto? {
        return checkRepository.findById(id.toUUID())
            .orElse(null)
            ?.takeIf { it.belongsTo(cashierId, deviceId) }
            ?.toDto()
    }

    fun findChecks(shiftId: String?, date: String?, since: Long?, cashierId: String, deviceId: String): List<CheckDto> {
        val entities = when {
            shiftId != null && since != null -> {
                checkRepository.findByShiftIdAndCreatedAtGreaterThanEqual(
                    shiftId.toUUID(),
                    since.toOffsetDateTime()
                )
            }
            shiftId != null -> checkRepository.findByShiftId(shiftId.toUUID())
            since != null -> checkRepository.findByCreatedAtGreaterThanEqual(since.toOffsetDateTime())
            else -> checkRepository.findAll()
        }

        return entities
            .asSequence()
            .filter { it.belongsTo(cashierId, deviceId) }
            .sortedByDescending { it.createdAt }
            .map { it.toDto() }
            .toList()
    }

    fun buildSalesReport(checks: List<CheckDto>, period: String): SalesReportDto {
        val sales = checks.filter { it.type.equals("SALE", ignoreCase = true) }
        val returns = checks.filter { it.type.equals("RETURN", ignoreCase = true) }
        val totalChecks = sales.size
        val returnChecks = returns.size
        val totalRevenue = sales.sumOf { it.total }
        val totalReturns = returns.sumOf { it.total }
        val cashRevenue = sales.filter { it.paymentType.equals("cash", ignoreCase = true) }.sumOf { it.total }
        val cardRevenue = sales.filter { it.paymentType.equals("card", ignoreCase = true) }.sumOf { it.total }
        val sbpRevenue = sales.filter { it.paymentType.equals("sbp", ignoreCase = true) }.sumOf { it.total }
        val averageCheck = if (totalChecks == 0) 0L else totalRevenue / totalChecks
        val topProducts = sales
            .flatMap { it.items }
            .groupBy { it.name }
            .map { (name, items) ->
                ProductSalesDto(
                    name = name,
                    quantity = items.sumOf { it.quantity },
                    total = items.sumOf { it.total }
                )
            }
            .sortedByDescending { it.total }

        return SalesReportDto(
            totalChecks = totalChecks,
            returnChecks = returnChecks,
            totalRevenue = totalRevenue,
            totalReturns = totalReturns,
            cashRevenue = cashRevenue,
            cardRevenue = cardRevenue,
            sbpRevenue = sbpRevenue,
            averageCheck = averageCheck,
            topProducts = topProducts
        )
    }

    fun buildMovementReport(checks: List<CheckDto>, documents: List<DocumentDto>, period: String, since: Long?): MovementReportDto {
        data class Acc(
            val key: String,
            var name: String,
            var opening: Double = 0.0,
            var income: Double = 0.0,
            var sales: Double = 0.0,
            var returns: Double = 0.0,
            var writeoff: Double = 0.0
        )

        fun productKey(productId: String?, barcode: String?, name: String): String {
            return when {
                !productId.isNullOrBlank() -> "product:$productId"
                !barcode.isNullOrBlank() -> "barcode:$barcode"
                else -> "name:${name.lowercase()}"
            }
        }

        val byProduct = linkedMapOf<String, Acc>()

        fun isBeforePeriod(timestamp: Long): Boolean = since != null && timestamp < since

        fun acc(key: String, name: String): Acc {
            val current = byProduct[key]
            if (current != null) {
                if (current.name.isBlank() && name.isNotBlank()) current.name = name
                return current
            }
            return Acc(key = key, name = name).also { byProduct[key] = it }
        }

        documents.forEach { doc ->
            when {
                doc.type.equals("ACCEPTANCE", ignoreCase = true) -> {
                    doc.items.forEach { item ->
                        val bucket = acc(productKey(item.productId, item.barcode, item.name), item.name)
                        if (isBeforePeriod(doc.timestamp)) {
                            bucket.opening += item.quantity
                        } else {
                            bucket.income += item.quantity
                        }
                    }
                }
                doc.type.equals("WRITEOFF", ignoreCase = true) -> {
                    doc.items.forEach { item ->
                        val bucket = acc(productKey(item.productId, item.barcode, item.name), item.name)
                        if (isBeforePeriod(doc.timestamp)) {
                            bucket.opening -= item.quantity
                        } else {
                            bucket.writeoff += item.quantity
                        }
                    }
                }
            }
        }

        checks.forEach { check ->
            when {
                check.type.equals("SALE", ignoreCase = true) -> {
                    check.items.forEach { item ->
                        val bucket = acc(productKey(item.productId, item.barcode, item.name), item.name)
                        if (isBeforePeriod(check.createdAt)) {
                            bucket.opening -= item.quantity
                        } else {
                            bucket.sales += item.quantity
                        }
                    }
                }
                check.type.equals("RETURN", ignoreCase = true) -> {
                    check.items.forEach { item ->
                        val bucket = acc(productKey(item.productId, item.barcode, item.name), item.name)
                        if (isBeforePeriod(check.createdAt)) {
                            bucket.opening += item.quantity
                        } else {
                            bucket.returns += item.quantity
                        }
                    }
                }
            }
        }

        val items = byProduct.values
            .map { v ->
                MovementReportItemDto(
                    name = v.name,
                    income = v.income,
                    sales = v.sales,
                    balance = v.opening + v.income - v.sales + v.returns - v.writeoff
                )
            }
            .sortedBy { it.name }

        val income = byProduct.values.sumOf { it.income }
        val sales = byProduct.values.sumOf { it.sales }
        val returns = byProduct.values.sumOf { it.returns }
        val writeoff = byProduct.values.sumOf { it.writeoff }
        val openingStock = byProduct.values.sumOf { it.opening }
        val closingStock = openingStock + income - sales + returns - writeoff

        return MovementReportDto(
            openingStock = openingStock,
            income = income,
            sales = sales,
            returns = returns,
            writeoff = writeoff,
            closingStock = closingStock,
            items = items
        )
    }

    private fun validateShiftOwnership(shiftId: String?, cashierId: String, deviceId: String) {
        if (shiftId == null) return

        val shift = shiftRepository.findById(shiftId.toUUID()).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        if (shift.cashierId != cashierId.toUUID() || shift.deviceId != deviceId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }
    }

    private fun CheckEntity.belongsTo(cashierId: String, deviceId: String): Boolean {
        return this.cashierId?.toString() == cashierId && this.deviceId == deviceId
    }

    private fun CheckDto.toEntity(cashierId: String, deviceId: String): CheckEntity {
        val checkEntity = CheckEntity(
            id = id.toUUID(),
            localUuid = localUuid,
            shiftId = shiftId?.toUUID(),
            cashierId = cashierId.toUUID(),
            deviceId = deviceId,
            type = type.uppercase(),
            fiscalSign = fiscalSign,
            ffdVersion = ffdVersion,
            subtotal = subtotal,
            discount = discount,
            total = total,
            taxAmount = taxAmount,
            paymentType = paymentType,
            createdAt = createdAt.toOffsetDateTime()
        )

        val itemEntities = items.map { item ->
            CheckItemEntity(
                id = item.id.toUUID(),
                check = checkEntity,
                productId = item.productId?.toUUID(),
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                discount = item.discount,
                vatRate = item.vatRate,
                total = item.total
            )
        }
        checkEntity.items.addAll(itemEntities)
        return checkEntity
    }

    private fun CheckEntity.toDto(): CheckDto {
        return CheckDto(
            id = id.toString(),
            localUuid = localUuid,
            shiftId = shiftId?.toString(),
            cashierId = cashierId?.toString(),
            deviceId = deviceId,
            type = type,
            fiscalSign = fiscalSign,
            ffdVersion = ffdVersion,
            subtotal = subtotal,
            discount = discount,
            total = total,
            taxAmount = taxAmount,
            paymentType = paymentType,
            items = items.map { item ->
                CheckItemDto(
                    id = item.id.toString(),
                    productId = item.productId?.toString(),
                    barcode = item.barcode,
                    name = item.name,
                    quantity = item.quantity,
                    price = item.price,
                    discount = item.discount,
                    vatRate = item.vatRate,
                    total = item.total
                )
            },
            createdAt = createdAt.toInstant().toEpochMilli()
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
