package com.vitbon.kkm.api.dto

data class LoginRequestDto(val pin: String)

data class LoginResponseDto(
    val token: String,
    val cashier: CashierDto
)

data class CashierDto(
    val id: String,
    val name: String,
    val role: String
)

data class CheckSyncRequestDto(val checks: List<CheckDto>)
data class CheckDto(
    val id: String,
    val localUuid: String,
    val shiftId: String?,
    val cashierId: String?,
    val deviceId: String,
    val type: String,
    val fiscalSign: String?,
    val ffdVersion: String?,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val taxAmount: Long,
    val paymentType: String?,
    val items: List<CheckItemDto>,
    val createdAt: Long
)
data class CheckItemDto(
    val id: String,
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Long,
    val discount: Long,
    val vatRate: String,
    val total: Long
)
data class CheckSyncResponseDto(val processed: Int, val failed: List<FailedCheckDto>)
data class FailedCheckDto(val localUuid: String, val error: String)

data class ProductSyncResponseDto(
    val products: List<ProductDto>,
    val deletedIds: List<String>,
    val serverTimestamp: Long
)
data class ProductDto(
    val id: String, val barcode: String?, val name: String, val article: String?,
    val price: Long, val vatRate: String, val categoryId: String?,
    val stock: Double, val egaisFlag: Boolean, val chaseznakFlag: Boolean, val updatedAt: Long
)

data class ShiftDto(
    val id: String, val cashierId: String, val deviceId: String,
    val openedAt: Long, val closedAt: Long?, val totalCash: Long, val totalCard: Long
)

data class DocumentDto(val type: String, val items: List<DocumentItemDto>, val timestamp: Long)
data class DocumentItemDto(val productId: String?, val barcode: String?, val name: String, val quantity: Double, val reason: String? = null)

data class LicenseCheckRequestDto(val deviceId: String)
data class LicenseCheckResponseDto(val status: String, val expiryDate: Long?, val graceUntil: Long?)
data class StatusResponseDto(val ofdQueueLength: Int, val lastSyncTimestamp: Long, val cloudServerOk: Boolean, val licenseStatus: String)

data class SalesReportDto(
    val totalChecks: Int,
    val totalRevenue: Long,
    val cashRevenue: Long,
    val cardRevenue: Long,
    val averageCheck: Long
)
