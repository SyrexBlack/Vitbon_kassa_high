package com.vitbon.kkm.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    val pin: String,
    val deviceId: String
)

data class LoginResponseDto(
    val token: String,
    val cashier: CashierDto,
    val features: LoginFeaturesDto = LoginFeaturesDto(),
    val expiresAt: Long
)

data class LoginFeaturesDto(
    @SerializedName("egaisEnabled")
    val egaisEnabled: Boolean = false,
    @SerializedName("chaseznakEnabled")
    val chaseznakEnabled: Boolean = false,
    @SerializedName("acquiringEnabled")
    val acquiringEnabled: Boolean = false,
    @SerializedName("sbpEnabled")
    val sbpEnabled: Boolean = false
)

data class CashierDto(
    val id: String,
    val name: String,
    val role: String  // ADMIN, SENIOR_CASHIER, CASHIER
)

data class CheckSyncRequestDto(
    val checks: List<CheckDto>
)

data class CheckDto(
    val id: String,
    val localUuid: String,
    val shiftId: String?,
    val cashierId: String?,
    val deviceId: String,
    val type: String,  // SALE, RETURN, CORRECTION, CASH_IN, CASH_OUT
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

data class CheckSyncResponseDto(
    val processed: Int,
    val failed: List<FailedCheckDto>
)

data class FailedCheckDto(
    val localUuid: String,
    val error: String
)

data class ProductSyncResponseDto(
    val products: List<ProductDto>,
    val deletedIds: List<String>,
    val serverTimestamp: Long
)

data class ProductDto(
    val id: String,
    val barcode: String?,
    val name: String,
    val article: String?,
    val price: Long,
    val vatRate: String,
    val categoryId: String?,
    val stock: Double,
    val egaisFlag: Boolean,
    val chaseznakFlag: Boolean,
    val updatedAt: Long
)

data class ShiftDto(
    val id: String,
    val cashierId: String,
    val deviceId: String,
    val openedAt: Long,
    val closedAt: Long?,
    val totalCash: Long,
    val totalCard: Long
)

data class DocumentDto(
    val type: String,  // ACCEPTANCE, WRITEOFF, INVENTORY
    val items: List<DocumentItemDto>,
    val timestamp: Long
)

data class DocumentItemDto(
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val reason: String? = null
)

data class LicenseCheckRequestDto(
    val deviceId: String
)

data class LicenseCheckResponseDto(
    val status: String,  // ACTIVE, EXPIRED, GRACE_PERIOD
    val expiryDate: Long?,
    val graceUntil: Long?
)

data class StatusResponseDto(
    val ofdQueueLength: Int,
    val lastSyncTimestamp: Long,
    val cloudServerOk: Boolean,
    val licenseStatus: String
)

data class ProductSalesDto(
    val name: String,
    val quantity: Double,
    val total: Long
)

data class SalesReportDto(
    val totalChecks: Int,
    val returnChecks: Int,
    val totalRevenue: Long,
    val totalReturns: Long,
    val cashRevenue: Long,
    val cardRevenue: Long,
    val averageCheck: Long,
    val topProducts: List<ProductSalesDto>? = null
)

data class MovementReportItemDto(
    val name: String,
    val income: Double,
    val sales: Double,
    val balance: Double
)

data class MovementReportDto(
    val openingStock: Double,
    val income: Double,
    val sales: Double,
    val returns: Double,
    val writeoff: Double,
    val closingStock: Double,
    val items: List<MovementReportItemDto>
)
