package com.vitbon.kkm.features.products.data

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.data.local.entity.LocalProduct
import com.vitbon.kkm.data.remote.dto.ProductDto
import java.util.UUID
import java.util.concurrent.TimeUnit

object ProductMapper {
    fun dtoToEntity(dto: ProductDto): LocalProduct {
        return LocalProduct(
            id = dto.id,
            barcode = dto.barcode,
            name = dto.name,
            article = dto.article,
            price = dto.price,
            vatRate = dto.vatRate,
            categoryId = dto.categoryId,
            stock = dto.stock,
            egaisFlag = dto.egaisFlag,
            chaseznakFlag = dto.chaseznakFlag,
            updatedAt = dto.updatedAt
        )
    }

    fun entityToDomain(entity: LocalProduct): com.vitbon.kkm.features.products.domain.Product {
        return com.vitbon.kkm.features.products.domain.Product(
            id = entity.id,
            barcode = entity.barcode,
            name = entity.name,
            article = entity.article,
            price = Money(entity.price),
            vatRate = VatRate.entries.find { it.name == entity.vatRate } ?: VatRate.NO_VAT,
            stock = entity.stock,
            egaisFlag = entity.egaisFlag,
            chaseznakFlag = entity.chaseznakFlag
        )
    }
}
