package com.vitbon.kkm.features.stock.domain

import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.dao.StockMovementDao
import com.vitbon.kkm.data.local.entity.StockMovement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Товар не найден или доступного остатка недостаточно для списания.
 */
data class StockConflict(
    val productId: String,
    val message: String
)

/**
 * Предпродажная проверка: какие изменения вносятся в остаток.
 */
data class StockMutation(
    val productId: String,
    val quantity: Double,
    val type: String // "SALE" | "RETURN" | "INCOME" | "WRITEOFF"
)

/**
 * Проверка остатка товара перед продажей. Детекция конфликтов
 * при мультиустройном изменении остатков.
 */
@Singleton
class StockConflictService @Inject constructor(
    private val productDao: ProductDao,
    private val movementDao: StockMovementDao
) {
    /**
     * Проверить конфликты продажи до выполнения.
     * Возвращает список конфликтов (пустой = можно продавать).
     */
    suspend fun checkSaleConflicts(mutations: List<StockMutation>): List<StockConflict> {
        val conflicts = mutableListOf<StockConflict>()
        mutations.forEach { mutation ->
            val product = productDao.findById(mutation.productId) ?: run {
                conflicts.add(StockConflict(mutation.productId, "Товар ${mutation.productId} не найден"))
                return@forEach
            }
            if (product.stock < mutation.quantity) {
                conflicts.add(
                    StockConflict(
                        mutation.productId,
                        "Недостаточно товара «${product.name}»: в наличии ${product.stock}, требуется ${mutation.quantity}"
                    )
                )
            }
        }
        return conflicts
    }

    /**
     * Сверка остатка товара по бухгалтерской ленте движений
     * с текущим остатком в карточке товара.
     */
    suspend fun verifyLedgerBalance(productId: String): LedgerBalanceResult {
        val product = productDao.findById(productId)
            ?: return LedgerBalanceResult(productId = productId)

        val movements = movementDao.findByProductId(productId)
        val productStock = product.stock

        val anchorMovement = movements
            .filter { it.isAnchor }
            .maxByOrNull { it.timestamp }
        val ledgerImplied = if (anchorMovement != null) {
            val deltasAfter = movements
                .filter { !it.isAnchor && it.timestamp >= anchorMovement.timestamp }
                .sumOf { it.delta }
            anchorMovement.delta + deltasAfter
        } else {
            movements.sumOf { it.delta }
        }

        val isBalanced = kotlin.math.abs(productStock - ledgerImplied) < 0.01

        return LedgerBalanceResult(
            productId = productId,
            ledgerBalance = ledgerImplied,
            productStock = productStock,
            discrepancy = if (isBalanced) 0.0 else productStock - ledgerImplied,
            hasConflict = !isBalanced,
            isBalanced = isBalanced
        )
    }

    /**
     * Записать движение товара (продажа, возврат, приход, списание).
     */
    suspend fun recordMovement(movement: StockMovement) {
        movementDao.insert(movement)
    }
}

data class LedgerBalanceResult(
    val productId: String,
    val ledgerBalance: Double = 0.0,
    val productStock: Double = 0.0,
    val discrepancy: Double = 0.0,
    val hasConflict: Boolean = false,
    val isBalanced: Boolean = false
)