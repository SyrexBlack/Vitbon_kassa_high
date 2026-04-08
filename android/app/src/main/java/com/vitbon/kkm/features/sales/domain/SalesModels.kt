package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.model.*

data class CartItem(
    val productId: String,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Money,
    val discount: Money = Money.ZERO,
    val vatRate: VatRate
) {
    val total: Money get() = Money(price.kopecks * quantity) - discount
}

data class Cart(
    val items: List<CartItem> = emptyList(),
    val globalDiscount: Money = Money.ZERO,
    val paymentType: PaymentType = PaymentType.CASH
) {
    val subtotal: Money get() = items.fold(Money.ZERO) { acc, item -> acc + item.total }
    val total: Money get() = subtotal - globalDiscount
    val taxAmount: Money get() = total * 0.22  // упрощённый расчёт НДС
}
