package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.persistence.CheckEntity
import com.vitbon.kkm.domain.persistence.CheckItemEntity
import com.vitbon.kkm.domain.persistence.CheckRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class CheckService(
    private val checkRepository: CheckRepository
) {

    @Transactional
    fun processSync(checks: List<CheckDto>): CheckSyncResponseDto {
        val failed = mutableListOf<FailedCheckDto>()

        checks.forEach { dto ->
            val entity = dto.toEntity()
            checkRepository.save(entity)
        }

        return CheckSyncResponseDto(checks.size, failed)
    }

    fun findChecks(shiftId: String?, date: String?, since: Long?): List<CheckDto> {
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
            .sortedByDescending { it.createdAt }
            .map { it.toDto() }
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
            averageCheck = averageCheck,
            topProducts = topProducts
        )
    }

    fun buildMovementReport(checks: List<CheckDto>, documents: List<DocumentDto>, period: String): MovementReportDto {
        data class Acc(
            val key: String,
            var name: String,
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
                        acc(productKey(item.productId, item.barcode, item.name), item.name).income += item.quantity
                    }
                }
                doc.type.equals("WRITEOFF", ignoreCase = true) -> {
                    doc.items.forEach { item ->
                        acc(productKey(item.productId, item.barcode, item.name), item.name).writeoff += item.quantity
                    }
                }
            }
        }

        checks.forEach { check ->
            when {
                check.type.equals("SALE", ignoreCase = true) -> {
                    check.items.forEach { item ->
                        acc(productKey(item.productId, item.barcode, item.name), item.name).sales += item.quantity
                    }
                }
                check.type.equals("RETURN", ignoreCase = true) -> {
                    check.items.forEach { item ->
                        acc(productKey(item.productId, item.barcode, item.name), item.name).returns += item.quantity
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
                    balance = v.income - v.sales + v.returns - v.writeoff
                )
            }
            .sortedBy { it.name }

        val income = byProduct.values.sumOf { it.income }
        val sales = byProduct.values.sumOf { it.sales }
        val returns = byProduct.values.sumOf { it.returns }
        val writeoff = byProduct.values.sumOf { it.writeoff }
        val openingStock = 0.0
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

    private fun CheckDto.toEntity(): CheckEntity {
        val checkEntity = CheckEntity(
            id = id.toUUID(),
            localUuid = localUuid,
            shiftId = shiftId?.toUUID(),
            cashierId = cashierId?.toUUID(),
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
