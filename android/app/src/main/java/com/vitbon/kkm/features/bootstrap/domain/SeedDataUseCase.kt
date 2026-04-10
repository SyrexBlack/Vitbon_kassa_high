package com.vitbon.kkm.features.bootstrap.domain

import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.data.local.dao.CashierDao
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.entity.LocalCashier
import com.vitbon.kkm.data.local.entity.LocalProduct
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataUseCase @Inject constructor(
    private val cashierDao: CashierDao,
    private val productDao: ProductDao
) {
    suspend fun seedIfNeeded() {
        if (cashierDao.count() == 0) {
            val now = System.currentTimeMillis()
            cashierDao.insert(
                LocalCashier(
                    id = "cashier-demo-1",
                    name = "Демо Кассир",
                    pinHash = sha256("1111"),
                    role = "CASHIER",
                    createdAt = now
                )
            )
        }

        if (productDao.count() == 0) {
            productDao.insert(
                LocalProduct(
                    id = "product-demo-water-05",
                    barcode = "4607001234567",
                    name = "Вода 0.5л",
                    article = "DEMO-WATER-05",
                    price = 12900,
                    vatRate = VatRate.NO_VAT.name,
                    categoryId = null,
                    stock = 100.0,
                    egaisFlag = false,
                    chaseznakFlag = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
