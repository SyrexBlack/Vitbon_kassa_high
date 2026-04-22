package com.vitbon.kkm.domain.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "checks")
class CheckEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "local_uuid", nullable = false)
    val localUuid: String,

    @Column(name = "shift_id")
    val shiftId: UUID?,

    @Column(name = "cashier_id")
    val cashierId: UUID?,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "fiscal_sign")
    val fiscalSign: String?,

    @Column(name = "ffd_version")
    val ffdVersion: String?,

    @Column(name = "subtotal", nullable = false)
    val subtotal: Long,

    @Column(name = "discount", nullable = false)
    val discount: Long,

    @Column(name = "total", nullable = false)
    val total: Long,

    @Column(name = "tax_amount", nullable = false)
    val taxAmount: Long,

    @Column(name = "payment_type")
    val paymentType: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime,

    @OneToMany(mappedBy = "check", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val items: MutableList<CheckItemEntity> = mutableListOf()
)

@Entity
@Table(name = "check_items")
class CheckItemEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", nullable = false)
    val check: CheckEntity,

    @Column(name = "product_id")
    val productId: UUID?,

    @Column(name = "barcode")
    val barcode: String?,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Double,

    @Column(name = "price", nullable = false)
    val price: Long,

    @Column(name = "discount", nullable = false)
    val discount: Long,

    @Column(name = "vat_rate", nullable = false)
    val vatRate: String,

    @Column(name = "total", nullable = false)
    val total: Long
)
