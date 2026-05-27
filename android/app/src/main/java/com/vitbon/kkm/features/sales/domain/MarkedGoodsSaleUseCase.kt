package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.features.chaseznak.domain.ChaseznakRepository
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.products.domain.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обёртка над ProcessSaleUseCase: валидирует и выбывает маркированные товары
 * до и после фискализации, обновляет остатки после успешной продажи.
 *
 * Поток:
 * 1. unmarked items → сразу в innerUseCase
 * 2. marked items → validateCode → innerUseCase → sell (по checkId)
 * 3. Блокировка: OK — продажа, всё остальное — SaleResult.FiscalError
 * 4. После успеха: decrementStock для каждой позиции
 */
@Singleton
class MarkedGoodsSaleUseCase @Inject constructor(
    private val chaseznakRepository: ChaseznakRepository,
    private val productRepository: ProductRepository,
    private val innerUseCase: ProcessSaleUseCase
) {
    suspend fun execute(
        cart: Cart,
        cashierId: String,
        deviceId: String,
        shiftId: String?,
        cashierRole: com.vitbon.kkm.features.auth.domain.CashierRole?,
        emergencySessionActive: Boolean
    ): SaleResult {
        for (item in cart.items) {
            val code = item.markedProductCode ?: continue
            val validation = chaseznakRepository.validateCode(code)
            if (validation.status != ChaseznakStatus.OK) {
                return SaleResult.FiscalError(
                    code = -1,
                    message = "CHASENAK_BLOCK: ${validation.status.name} — ${validation.message ?: "код невозможно продать"}"
                )
            }
        }

        val result = innerUseCase.execute(cart, cashierId, deviceId, shiftId, cashierRole, emergencySessionActive)
        if (result is SaleResult.Success) {
            for (item in cart.items) {
                productRepository.decrementStock(item.productId, item.quantity)
            }
            for (item in cart.items) {
                val code = item.markedProductCode ?: continue
                try {
                    chaseznakRepository.sell(code, result.checkId)
                } catch (_: Throwable) {
                    // выбытие — best-effort; фискальный результат уже получен
                }
            }
        }
        return result
    }
}