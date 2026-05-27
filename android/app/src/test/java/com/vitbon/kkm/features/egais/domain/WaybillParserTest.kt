package com.vitbon.kkm.features.egais.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD — vitbon-kassa-1rd.1.2: Parse incoming waybill XML into structured data.
 */
class WaybillParserTest {

    private val parser = WaybillParser()

    @Test
    fun `parse valid waybill XML extracts items correctly`() {
        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-001</ns:Identity>
                <ns:Header>
                    <ns:IsAccept>Accepted</ns:IsAccept>
                    <ns:Quantity>2</ns:Quantity>
                </ns:Header>
                <ns:Content>
                    <ns:Position>
                        <ns:Product>
                            <ns:Name>Пиво светлое 0.5л</ns:Name>
                            <ns:Barcode>4607123456</ns:Barcode>
                        </ns:Product>
                        <ns:Quantity>24</ns:Quantity>
                        <ns:Price>85.50</ns:Price>
                    </ns:Position>
                    <ns:Position>
                        <ns:Product>
                            <ns:Name>Вино красное 0.75л</ns:Name>
                            <ns:Barcode>4607890123</ns:Barcode>
                        </ns:Product>
                        <ns:Quantity>12</ns:Quantity>
                        <ns:Price>320.00</ns:Price>
                    </ns:Position>
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val waybill = parser.parseWaybillXml(xml)

        assertEquals("WB-2024-001", waybill.egaisId)
        assertEquals(2, waybill.items.size)
        assertEquals("Пиво светлое 0.5л", waybill.items[0].productName)
        assertEquals(24.0, waybill.items[0].volume, 0.01)
        assertEquals(85.50, waybill.items[0].price, 0.01)
        assertTrue(waybill.items[0].barcodes.contains("4607123456"))
        assertEquals("Вино красное 0.75л", waybill.items[1].productName)
        assertEquals(12.0, waybill.items[1].volume, 0.01)
    }

    @Test
    fun `parse waybill with multiple barcodes extracts all`() {
        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-002</ns:Identity>
                <ns:Header><ns:IsAccept>Accepted</ns:IsAccept><ns:Quantity>1</ns:Quantity></ns:Header>
                <ns:Content>
                    <ns:Position>
                        <ns:Product>
                            <ns:Name>Водка 0.5л</ns:Name>
                            <ns:Barcodes>
                                <ns:Barcode>4601234567</ns:Barcode>
                                <ns:Barcode>4601234568</ns:Barcode>
                            </ns:Barcodes>
                        </ns:Product>
                        <ns:Quantity>6</ns:Quantity>
                        <ns:Price>450.00</ns:Price>
                    </ns:Position>
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val waybill = parser.parseWaybillXml(xml)

        assertEquals(2, waybill.items[0].barcodes.size)
        assertTrue(waybill.items[0].barcodes.containsAll(listOf("4601234567", "4601234568")))
    }

    @Test
    fun `parse invalid XML returns error result`() {
        val xml = "<invalid>not a waybill</invalid>"

        val result = parser.tryParseWaybillXml(xml)

        assertTrue(result is ParseResult.Error)
        assertTrue((result as ParseResult.Error).message.contains("не удалось", ignoreCase = true))
    }
}