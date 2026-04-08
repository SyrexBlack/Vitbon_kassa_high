package com.vitbon.kkm.features.products.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.entity.LocalProduct
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {
    fun observeAll(): Flow<List<LocalProduct>> = productDao.observeAll()

    /** Найти товар по штрихкоду */
    suspend fun findByBarcode(barcode: String): LocalProduct? {
        return productDao.findByBarcode(barcode)
    }

    suspend fun findById(id: String): LocalProduct? {
        return productDao.findById(id)
    }

    /** Поиск по названию или артикулу */
    suspend fun search(query: String): List<LocalProduct> {
        return productDao.search(query)
    }

    /** Сохранить товар (например, при локальном создании) */
    suspend fun save(product: LocalProduct) {
        productDao.insert(product)
    }

    /** Заменить весь каталог (после полной синхронизации) */
    suspend fun replaceAll(products: List<LocalProduct>) {
        productDao.deleteAll()
        productDao.insertAll(products)
    }

    /** Синхронизировать delta — вставить/обновить */
    suspend fun upsertAll(products: List<LocalProduct>) {
        productDao.insertAll(products)
    }

    /** Обновить остаток товара после продажи */
    suspend fun decrementStock(productId: String, quantity: Double) {
        val product = productDao.findById(productId) ?: return
        val newStock = (product.stock - quantity).coerceAtLeast(0.0)
        productDao.insert(product.copy(stock = newStock))
    }
}

/** Маппинг LocalProduct → доменная модель */
fun LocalProduct.toDomain(): Product {
    return Product(
        id = id,
        barcode = barcode,
        name = name,
        article = article,
        price = Money(price),
        vatRate = VatRate.entries.find { it.name == vatRate } ?: VatRate.NO_VAT,
        stock = stock,
        egaisFlag = egaisFlag,
        chaseznakFlag = chaseznakFlag
    )
}

data class Product(
    val id: String,
    val barcode: String?,
    val name: String,
    val article: String?,
    val price: Money,
    val vatRate: VatRate,
    val stock: Double,
    val egaisFlag: Boolean,
    val chaseznakFlag: Boolean
)
