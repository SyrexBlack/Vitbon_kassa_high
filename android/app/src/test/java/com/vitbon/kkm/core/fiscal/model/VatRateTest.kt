package com.vitbon.kkm.core.fiscal.model

import org.junit.Assert.*
import org.junit.Test

class VatRateTest {

    @Test
    fun `NO_VAT tag is numeric 6 not no_vat string`() {
        assertEquals("6", VatRate.NO_VAT.tag)
    }

    @Test
    fun `all VAT rates have numeric tags`() {
        val numericTags = listOf(VatRate.VAT_22, VatRate.VAT_10, VatRate.VAT_0, VatRate.VAT_5, VatRate.VAT_7, VatRate.NO_VAT)
        for (rate in numericTags) {
            assertTrue("Tag for ${rate.name} must be numeric, got: ${rate.tag}", rate.tag.matches(Regex("\\d+")))
        }
    }

    @Test
    fun `TaxSystem fromString returns correct enum`() {
        assertEquals(TaxSystem.OSN, TaxSystem.fromString("1"))
        assertEquals(TaxSystem.USN_INCOME, TaxSystem.fromString("2"))
        assertEquals(TaxSystem.ESN, TaxSystem.fromString("4"))
        assertEquals(TaxSystem.USN_INCOME_OUTCOME, TaxSystem.fromString("5"))
        assertEquals(TaxSystem.PSN, TaxSystem.fromString("6"))
    }

    @Test
    fun `TaxSystem tag values are correct`() {
        assertEquals("1", TaxSystem.OSN.tag)
        assertEquals("2", TaxSystem.USN_INCOME.tag)
        assertEquals("4", TaxSystem.ESN.tag)
        assertEquals("5", TaxSystem.USN_INCOME_OUTCOME.tag)
        assertEquals("6", TaxSystem.PSN.tag)
    }
}
