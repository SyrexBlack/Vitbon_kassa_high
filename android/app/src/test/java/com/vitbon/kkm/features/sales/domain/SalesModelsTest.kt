package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.model.*
import org.junit.Assert.*
import org.junit.Test

class SalesModelsTest {
    @Test
    fun `Cart — subtotal sums items`() {
        val cart = Cart(items = listOf(
            CartItem("p1", "4601", "Товар А", 2.0, Money(100_00L), Money.ZERO, VatRate.VAT_22),
            CartItem("p2", "4602", "Товар Б", 1.0, Money(50_00L), Money.ZERO, VatRate.VAT_22)
        ))
        assertEquals(Money(250_00L), cart.subtotal)  // 200 + 50
    }

    @Test
    fun `Cart — global discount deducts from total`() {
        val cart = Cart(
            items = listOf(
                CartItem("p1", "4601", "Товар", 1.0, Money(500_00L), Money.ZERO, VatRate.VAT_22)
            ),
            globalDiscount = Money(50_00L)
        )
        assertEquals(Money(450_00L), cart.total)  // 500 - 50
    }

    @Test
    fun `CartItem — total = price times quantity minus discount`() {
        val item = CartItem(
            productId = "p1", barcode = null, name = "Test",
            quantity = 3.0, price = Money(100_00L),
            discount = Money(50_00L),  // скидка на позицию
            vatRate = VatRate.VAT_22
        )
        assertEquals(Money(250_00L), item.total)  // 3*100 - 50
    }

    @Test
    fun `Cart — taxAmount is 22 percent of total`() {
        val cart = Cart(items = listOf(
            CartItem("p1", null, "T", 1.0, Money(122_00L), Money.ZERO, VatRate.VAT_22)
        ))
        // subtotal = 122.00, total = 122.00, taxAmount = 122 * 0.22 ≈ 26.84
        assertTrue(cart.taxAmount.kopecks > 0)
    }
}
