package com.vitbon.kkm.core.fiscal.model

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test
    fun `Money — plus and minus`() {
        val a = Money(100_00L)   // 100.00
        val b = Money(30_50L)    // 30.50
        assertEquals(Money(130_50L), a + b)
        assertEquals(Money(69_50L), a - b)
    }

    @Test
    fun `Money — multiply by factor`() {
        val price = Money(1000_00L)  // 1000.00
        assertEquals(Money(500_00L), price * 0.5)
        assertEquals(Money(1500_00L), price * 1.5)
    }

    @Test
    fun `Money — fromRubles and rubles conversion`() {
        assertEquals(Money(999L), Money.fromRubles(9.999))
        assertEquals(9.99, Money(999L).rubles, 0.001)
    }

    @Test
    fun `Money — ZERO constant`() {
        assertEquals(Money(0L), Money.ZERO)
        assertEquals(Money(100L), Money.ZERO + Money(100L))
    }

    @Test
    fun `CheckItem — requires positive quantity`() {
        val ex = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CheckItem(
                id = "i1", productId = "p1", barcode = null,
                name = "Test", quantity = 0.0,
                price = Money(100L), vatRate = VatRate.VAT_22
            )
        }
        assertTrue(ex.message!!.contains("quantity"))
    }

    @Test
    fun `FFDVersion — fromString parsing`() {
        assertEquals(FFDVersion.V1_05, FFDVersion.fromString("1.05"))
        assertEquals(FFDVersion.V1_05, FFDVersion.fromString("1_05"))
        assertEquals(FFDVersion.V1_2, FFDVersion.fromString("1.2"))
        assertEquals(FFDVersion.V1_2, FFDVersion.fromString("1_2"))
        assertEquals(FFDVersion.V1_05, FFDVersion.fromString(null))
        assertEquals(FFDVersion.V1_05, FFDVersion.fromString("unknown"))
    }
}
