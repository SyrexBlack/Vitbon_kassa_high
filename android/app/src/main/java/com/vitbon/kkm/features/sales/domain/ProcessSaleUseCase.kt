package com.vitbon.kkm.features.sales.domain

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
class ProcessSaleUseCase @Inject constructor(
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao
) {
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?
    ): SaleResult {
        // 1. Построить FiscalCheck
        val fiscalCheck = FiscalCheck(
            id = UUID.randomUUID().toString(),
            type = CheckType.SALE,
            items = cart.items.map { item ->
                CheckItem(
                    id = UUID.randomUUID().toString(),
                    productId = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    quantity = item.quantity,
                    price = item.price,
                    discount = item.discount,
                    vatRate = item.vatRate,
                    total = item.total
                )
            },
            payments = listOf(
                PaymentLine(
                    type = cart.paymentType,
                    amount = cart.total,
                    label = cart.paymentType.name
                )
            )
        )

        // 2. Сохранить в Room (черновик — PENDING_SYNC)
        val localCheck = LocalCheck(
            id = fiscalCheck.id,
            localUuid = fiscalCheck.id,
            shiftId = shiftId,
            cashierId = cashierId,
            deviceId = deviceId,
            type = CheckType.SALE.value,
            fiscalSign = null,
            ofdResponse = null,
            ffdVersion = null,
            status = "PENDING_SYNC",
            subtotal = cart.subtotal.kopecks,
            discount = cart.globalDiscount.kopecks,
            total = cart.total.kopecks,
            taxAmount = cart.taxAmount.kopecks,
            paymentType = cart.paymentType.value,
            createdAt = System.currentTimeMillis(),
            syncedAt = null
        )
        checkDao.insert(localCheck)

        val localItems = fiscalCheck.items.map { item ->
            LocalCheckItem(
                id = item.id,
                checkId = fiscalCheck.id,
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

        // 3. Отправить в FiscalRuntime
        val fiscalResult = fiscalOrchestrator.executeSale(fiscalCheck)

        return when (fiscalResult) {
            is FiscalRuntimeResult.Success -> {
                checkDao.updateSyncStatus(
                    id = fiscalCheck.id,
                    status = "PENDING_SYNC",
                    fiscalSign = fiscalResult.fiscalSign,
                    ofdResponse = null,
                    syncedAt = null
                )
                SaleResult.Success(
                    checkId = fiscalCheck.id,
                    fiscalSign = fiscalResult.fiscalSign,
                    total = cart.total.rubles
                )
            }
            is FiscalRuntimeResult.Error -> {
                checkDao.updateSyncStatus(
                    id = fiscalCheck.id,
                    status = "FISCAL_ERROR",
                    fiscalSign = null,
                    ofdResponse = null,
                    syncedAt = null
                )
                SaleResult.FiscalError(-1, fiscalResult.message)
            }
        }
    }
}

sealed class SaleResult {
    data class Success(val checkId: String, val fiscalSign: String, val total: Double) : SaleResult()
    data class FiscalError(val code: Int, val message: String) : SaleResult()
}
