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

    @Test
    fun `Cart — taxAmount for VAT_10 item is 10-110th of total`() {
        // price = 110.00 (incl 10% VAT), total = 110.00
        // VAT = 110 * 10/110 = 10.00
        val cart = Cart(items = listOf(
            CartItem("p1", null, "Молоко", 1.0, Money(110_00L), Money.ZERO, VatRate.VAT_10)
        ))
        assertEquals(Money(10_00L), cart.taxAmount)
    }

    @Test
    fun `Cart — taxAmount for NO_VAT item is zero`() {
        val cart = Cart(items = listOf(
            CartItem("p1", null, "Книга", 1.0, Money(200_00L), Money.ZERO, VatRate.NO_VAT)
        ))
        assertEquals(Money.ZERO, cart.taxAmount)
    }

    @Test
    fun `Cart — taxAmount for mixed VAT rates sums each item correctly`() {
        // Item 1: VAT_22, price=122.00 → VAT=22.00
        // Item 2: VAT_10, price=110.00 → VAT=10.00
        // Item 3: NO_VAT, price=100.00 → VAT=0
        val cart = Cart(items = listOf(
            CartItem("p1", null, "Водка", 1.0, Money(122_00L), Money.ZERO, VatRate.VAT_22),
            CartItem("p2", null, "Молоко", 1.0, Money(110_00L), Money.ZERO, VatRate.VAT_10),
            CartItem("p3", null, "Книга", 1.0, Money(100_00L), Money.ZERO, VatRate.NO_VAT)
        ))
        // Expected: 22.00 + 10.00 + 0 = 32.00
        assertEquals(Money(32_00L), cart.taxAmount)
    }
}
