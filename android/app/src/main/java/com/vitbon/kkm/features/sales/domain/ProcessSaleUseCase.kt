package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.data.local.entity.LocalCheckItem
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.auth.domain.RoleOperation
import com.vitbon.kkm.features.auth.domain.RolePolicy
import com.vitbon.kkm.features.egais.domain.AlcoholSaleDecision
import com.vitbon.kkm.features.egais.domain.AlcoholSalePolicyUseCase
import com.vitbon.kkm.features.products.domain.Product
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSaleUseCase @Inject constructor(
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao,
    private val shiftDao: ShiftDao,
    private val fiscalConfig: FiscalConfig,
    private val alcoholSalePolicy: AlcoholSalePolicyUseCase
) {
    private suspend fun buildAdditionalInfo(): Map<String, String> {
        val shift = shiftDao.findOpenShift() ?: return emptyMap()
        return try {
            val status = fiscalOrchestrator.executeStatusCheck()
            buildMap {
                put("shiftNumber", shift.id.toString())
                put("receiptNumberInShift", (status.currentFdNumber + 1).toString())
                put("taxSystem", fiscalConfig.taxSystem.tag)
                put("orgInn", fiscalConfig.orgInn ?: "")
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    /**
     * Run the sale pipeline.
     *
     * Pre-fiscal checks (in order):
     *  1. Role policy — fail-closed for non-cashier roles or active emergency session
     *  2. Alcohol policy (vitbon-kassa-1rd.1.3) — if any cart item is EGAIS-flagged
     *     and [ageVerificationDone] is false or EGAIS is unavailable, the sale is
     *     blocked *before* any fiscal document is opened.
     *
     * @param cart items being sold
     * @param cashierId acting cashier UUID
     * @param deviceId fiscal device id
     * @param shiftId open-shift id (nullable for legacy clients)
     * @param cashierRole acting role; null → no SALE permission
     * @param emergencySessionActive when true, sales are blocked (audit only)
     * @param ageVerificationDone MAX-ID age confirmation result for the buyer.
     *        Must be true for any EGAIS-flagged item. Defaults to false so that
     *        callers who don't yet pass it remain fail-closed for alcohol.
     */
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: CashierRole?,
        emergencySessionActive: Boolean,
        ageVerificationDone: Boolean = false
    ): SaleResult {
        if (emergencySessionActive || !RolePolicy.canPerform(cashierRole, RoleOperation.SALE)) {
            return SaleResult.FiscalError(-1, RolePolicy.ACCESS_DENIED_MESSAGE)
        }

        // vitbon-kassa-1rd.1.3: pre-fiscal alcohol policy check (fail-closed)
        val alcoholCheck = enforceAlcoholPolicy(cart, ageVerificationDone)
        if (alcoholCheck != null) return alcoholCheck

        val additionalInfo = buildAdditionalInfo()
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
                    total = item.total,
                    markedProductCode = item.markedProductCode
                )
            },
            payments = listOf(
                PaymentLine(
                    type = cart.paymentType,
                    amount = cart.total,
                    label = cart.paymentType.name
                )
            ),
            additionalInfo = additionalInfo
        )

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
                total = item.total.kopecks,
                markedProductCode = item.markedProductCode
            )
        }
        checkItemDao.insertAll(localItems)

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

    /**
     * Convert cart items into the [Product] shape the alcohol policy expects, then
     * delegate the decision. Returns a non-null [SaleResult] only when the sale
     * must be blocked.
     *
     * The lookup is intentionally driven by [CartItem.egaisFlag] (populated by
     * [ScanBarcodeUseCase] from the local catalog) so we don't need an extra
     * DB round-trip per item.
     */
    private suspend fun enforceAlcoholPolicy(
        cart: Cart,
        ageVerificationDone: Boolean
    ): SaleResult.FiscalError? {
        val alcoholProducts = cart.items
            .filter { it.egaisFlag }
            .map { item ->
                Product(
                    id = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    article = null,
                    price = item.price,
                    vatRate = item.vatRate,
                    stock = 0.0,
                    egaisFlag = true,
                    chaseznakFlag = false
                )
            }
        if (alcoholProducts.isEmpty()) return null

        return when (val decision = alcoholSalePolicy.checkCanSellAlcohol(
                products = alcoholProducts,
                ageVerificationDone = ageVerificationDone
            )) {
            AlcoholSaleDecision.Allowed -> null
            is AlcoholSaleDecision.AgeVerificationRequired -> SaleResult.FiscalError(
                code = -1,
                message = "Требуется подтверждение возраста покупателя (MAX-ID). Товары: ${decision.products.joinToString()}"
            )
            is AlcoholSaleDecision.Blocked -> SaleResult.FiscalError(
                code = -1,
                message = decision.message
            )
        }
    }
}

sealed class SaleResult {
    data class Success(val checkId: String, val fiscalSign: String, val total: Double) : SaleResult()
    data class FiscalError(val code: Int, val message: String) : SaleResult()
    data class TerminalError(val reason: String, val message: String) : SaleResult()
}