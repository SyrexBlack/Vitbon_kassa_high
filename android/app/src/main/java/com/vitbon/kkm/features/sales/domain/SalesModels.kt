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
    val total: Money get() = Money((price.kopecks * quantity).toLong()) - discount
}

data class Cart(
    val items: List<CartItem> = emptyList(),
    val globalDiscount: Money = Money.ZERO,
    val paymentType: PaymentType = PaymentType.CASH
) {
    val subtotal: Money get() = items.fold(Money.ZERO) { acc, item -> acc + item.total }
    val total: Money get() = subtotal - globalDiscount

    /**
     * Сумма НДС по чеку.
     * Для ставки НДС 22%:  tax = total * 22/122
     * Для ставки НДС 10%:  tax = total * 10/110
     * Для НДС 0%, 5%, 7%:  tax = 0
     * Для БЕЗ НДС:         tax = 0
     */
    val taxAmount: Money get() {
        if (items.isEmpty()) return Money.ZERO
        val rates = items.groupBy { it.vatRate }
        var tax = 0L
        for ((rate, items) in rates) {
            val rateSum = items.sumOf { it.total.kopecks }
            tax += when (rate) {
                VatRate.VAT_22 -> (rateSum * 22.0 / 122.0).toLong()
                VatRate.VAT_10 -> (rateSum * 10.0 / 110.0).toLong()
                VatRate.VAT_0, VatRate.VAT_5, VatRate.VAT_7, VatRate.NO_VAT -> 0L
            }
        }
        return Money(tax)
    }
}
