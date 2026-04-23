package com.vitbon.kkm.features.returns.domain

import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.data.local.entity.LocalCheckItem
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReturnUseCase @Inject constructor(
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao
) {
    suspend fun findCheckByQr(qrData: String): LocalCheck? {
        val normalized = qrData.trim()
        if (normalized.isEmpty()) return null

        val payload = normalized.substringAfter('?', normalized)
        val fp = payload.split('&')
            .asSequence()
            .mapNotNull { token ->
                val separatorIndex = token.indexOf('=')
                if (separatorIndex <= 0 || separatorIndex == token.lastIndex) return@mapNotNull null
                val key = token.substring(0, separatorIndex).trim().lowercase()
                val value = token.substring(separatorIndex + 1).trim()
                if (key == "fp" && value.isNotEmpty()) value else null
            }
            .firstOrNull()

        return fp?.let { checkDao.findLatestSaleByIdentifier(it) }
    }

    suspend fun findCheckByNumber(checkNumber: String): LocalCheck? {
        val normalizedInput = checkNumber.trim()
        if (normalizedInput.isEmpty()) return null
        return checkDao.findLatestSaleByIdentifier(normalizedInput)
    }

    suspend fun loadCheckItems(checkId: String): List<ReturnItem> {
        return checkItemDao.findByCheckId(checkId).map { item ->
            ReturnItem(
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                price = Money(item.price),
                discount = Money(item.discount),
                vatRate = runCatching { VatRate.valueOf(item.vatRate) }.getOrDefault(VatRate.NO_VAT)
            )
        }
    }

    suspend fun processReturn(
        originalCheck: LocalCheck,
        items: List<ReturnItem>,
        cashierId: String
    ): ReturnResult {
        val fiscalItems = items.map { item ->
            val lineSubtotal = (item.price.kopecks * item.quantity).toLong()
            val lineTotal = lineSubtotal - item.discount.kopecks
            CheckItem(
                id = UUID.randomUUID().toString(),
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                discount = item.discount,
                vatRate = item.vatRate,
                total = Money(lineTotal)
            )
        }

        val returnCheck = FiscalCheck(
            id = UUID.randomUUID().toString(),
            type = CheckType.RETURN,
            items = fiscalItems,
            payments = listOf(
                PaymentLine(
                    PaymentType.CASH,
                    Money(fiscalItems.sumOf { it.total.kopecks }),
                    "Возврат"
                )
            )
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
            subtotal = fiscalItems.sumOf { (it.price.kopecks * it.quantity).toLong() },
            discount = fiscalItems.sumOf { it.discount.kopecks },
            total = fiscalItems.sumOf { it.total.kopecks },
            taxAmount = 0,
            paymentType = PaymentType.CASH.value,
            createdAt = System.currentTimeMillis(),
            syncedAt = null
        )
        checkDao.insert(localCheck)

        val localItems = fiscalItems.map { item ->
            LocalCheckItem(
                id = item.id,
                checkId = returnCheck.id,
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                quantity = item.quantity,
                price = item.price.kopecks,
                discount = item.discount.kopecks,
                vatRate = item.vatRate.name,
                total = item.total.kopecks
            )
        }
        checkItemDao.insertAll(localItems)

        val fiscalResult = fiscalOrchestrator.executeReturn(returnCheck)
        return when (fiscalResult) {
            is FiscalRuntimeResult.Success -> {
                checkDao.updateSyncStatus(
                    id = returnCheck.id,
                    status = "PENDING_SYNC",
                    fiscalSign = fiscalResult.fiscalSign,
                    ofdResponse = null,
                    syncedAt = null
                )
                ReturnResult.Success(returnCheck.id, fiscalResult.fiscalSign)
            }
            is FiscalRuntimeResult.Error -> {
                checkDao.updateSyncStatus(
                    id = returnCheck.id,
                    status = "FISCAL_ERROR",
                    fiscalSign = null,
                    ofdResponse = null,
                    syncedAt = null
                )
                ReturnResult.FiscalError(-1, fiscalResult.message)
            }
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
