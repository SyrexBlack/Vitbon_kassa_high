package com.vitbon.kkm.features.returns.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReturnUseCase @Inject constructor(
    private val fiscalCore: FiscalCore,
    private val checkDao: CheckDao
) {
    suspend fun findCheckByQr(qrData: String): LocalCheck? {
        // QR содержит JSON с fiscalSign или localUuid
        // { "fs": "SIGN123", "dt": "2024-01-01", "sum": 1500 }
        val check = try {
            checkDao.findByLocalUuid(qrData)
        } catch (e: Exception) {
            null
        }
        return check ?: checkDao.findPendingSync().lastOrNull() // fallback
    }

    suspend fun findCheckByNumber(checkNumber: String): LocalCheck? {
        return checkDao.findPendingSync().lastOrNull()
    }

    suspend fun processReturn(
        originalCheck: LocalCheck,
        items: List<ReturnItem>,
        cashierId: String
    ): ReturnResult {
        val returnCheck = FiscalCheck(
            id = UUID.randomUUID().toString(),
            type = CheckType.RETURN,
            items = items.map { item ->
                CheckItem(
                    id = UUID.randomUUID().toString(),
                    productId = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    quantity = item.quantity,
                    price = item.price,
                    discount = item.discount,
                    vatRate = item.vatRate,
                    total = Money(item.price.kopecks * item.quantity)
                )
            },
            payments = listOf(PaymentLine(PaymentType.CASH, Money(items.sumOf { it.price.kopecks * it.quantity }), "Возврат"))
        )

        // Сохранить в Room
        val localCheck = LocalCheck(
            id = returnCheck.id,
            localUuid = returnCheck.id,
            shiftId = originalCheck.shiftId,
            cashierId = cashierId,
            deviceId = originalCheck.deviceId,
            type = CheckType.RETURN.value,
            fiscalSign = null,
            ofdResponse = null,
            ffdVersion = null,
            status = "PENDING_SYNC",
            subtotal = items.sumOf { it.price.kopecks * it.quantity },
            discount = 0,
            total = items.sumOf { it.price.kopecks * it.quantity },
            taxAmount = 0,
            paymentType = PaymentType.CASH.value,
            createdAt = System.currentTimeMillis(),
            syncedAt = null
        )
        checkDao.insert(localCheck)

        val fiscalResult = fiscalCore.printReturn(returnCheck)
        return when (fiscalResult) {
            is FiscalResult.Success -> ReturnResult.Success(returnCheck.id, fiscalResult.fiscalSign)
            is FiscalResult.Error -> ReturnResult.FiscalError(fiscalResult.code, fiscalResult.message)
        }
    }
}

data class ReturnItem(
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Money,
    val discount: Money = Money.ZERO,
    val vatRate: VatRate
)

sealed class ReturnResult {
    data class Success(val checkId: String, val fiscalSign: String) : ReturnResult()
    data class FiscalError(val code: Int, val message: String) : ReturnResult()
}
