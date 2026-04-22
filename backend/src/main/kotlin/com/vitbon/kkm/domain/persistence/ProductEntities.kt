package com.vitbon.kkm.domain.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "products")
class ProductEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "barcode")
    val barcode: String?,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "article")
    val article: String?,

    @Column(name = "price", nullable = false)
    val price: Long,

    @Column(name = "vat_rate", nullable = false)
    val vatRate: String,

    @Column(name = "category_id")
    val categoryId: UUID?,

    @Column(name = "stock")
    val stock: Double,

    @Column(name = "egais_flag", nullable = false)
    val egaisFlag: Boolean,

    @Column(name = "chaseznak_flag", nullable = false)
    val chaseznakFlag: Boolean,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime
)
