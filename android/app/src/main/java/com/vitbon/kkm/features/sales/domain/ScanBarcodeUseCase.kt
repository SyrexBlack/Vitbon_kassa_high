package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.features.products.domain.Product
import com.vitbon.kkm.features.products.domain.ProductRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanBarcodeUseCase @Inject constructor(
    private val productRepo: ProductRepository
) {
    suspend fun execute(barcode: String): ScanResult {
        if (barcode.isBlank()) return ScanResult.NotFound(barcode)
        val product = productRepo.findByBarcode(barcode)
        return if (product != null) {
            ScanResult.Found(
                CartItem(
                    productId = product.id,
                    barcode = product.barcode,
                    name = product.name,
                    quantity = 1.0,
                    price = product.price,
                    vatRate = product.vatRate
                )
            )
        } else {
            ScanResult.NotFound(barcode)
        }
    }
}

sealed class ScanResult {
    data class Found(val item: CartItem) : ScanResult()
    data class NotFound(val barcode: String) : ScanResult()
}
