package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.persistence.ProductEntity
import com.vitbon.kkm.domain.persistence.ProductRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    fun getProductsDelta(since: Long?): ProductSyncResponseDto {
        val products = if (since == null) {
            productRepository.findAllByOrderByUpdatedAtAsc()
        } else {
            productRepository.findByUpdatedAtGreaterThanEqualOrderByUpdatedAtAsc(since.toOffsetDateTime())
        }

        return ProductSyncResponseDto(
            products = products.map { it.toDto() },
            deletedIds = emptyList(),
            serverTimestamp = System.currentTimeMillis()
        )
    }

    private fun ProductEntity.toDto(): ProductDto {
        return ProductDto(
            id = id.toString(),
            barcode = barcode,
            name = name,
            article = article,
            price = price,
            vatRate = vatRate,
            categoryId = categoryId?.toString(),
            stock = stock,
            egaisFlag = egaisFlag,
            chaseznakFlag = chaseznakFlag,
            updatedAt = updatedAt.toInstant().toEpochMilli()
        )
    }

    private fun Long.toOffsetDateTime(): OffsetDateTime {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
    }
}
