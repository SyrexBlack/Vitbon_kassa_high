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
@Table(name = "documents")
class DocumentEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "timestamp", nullable = false)
    val timestamp: OffsetDateTime,

    @OneToMany(mappedBy = "document", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val items: MutableList<DocumentItemEntity> = mutableListOf()
)

@Entity
@Table(name = "document_items")
class DocumentItemEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    val document: DocumentEntity,

    @Column(name = "product_id")
    val productId: UUID?,

    @Column(name = "barcode")
    val barcode: String?,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "quantity", nullable = false)
    val quantity: Double,

    @Column(name = "reason")
    val reason: String?
)
