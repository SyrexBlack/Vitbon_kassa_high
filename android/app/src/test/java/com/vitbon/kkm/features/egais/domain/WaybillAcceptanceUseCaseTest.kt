package com.vitbon.kkm.features.egais.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD — vitbon-kassa-1rd.1.2: Full incoming waybill acceptance flow.
 * 1. Parse XML into structured waybill
 * 2. Validate items exist and quantities are positive
 * 3. Send to EGAIS via repository
 * 4. Return structured result
 */
class WaybillAcceptanceUseCaseTest {

    private val repository = mockk<EgaisRepository>(relaxed = true)
    private val useCase = WaybillAcceptanceUseCase(repository)

    @Test
    fun `valid waybill XML parsed and accepted returns success`() = runTest {
        coEvery { repository.acceptIncomingWaybill(any()) } returns
            EgaisResult.Success(egaisId = "WB-EGAIS-001", message = "Накладная загружена")

        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-001</ns:Identity>
                <ns:Header>
                    <ns:IsAccept>Accepted</ns:IsAccept>
                    <ns:Quantity>1</ns:Quantity>
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
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Success)
        assertEquals("WB-EGAIS-001", (result as WaybillAcceptanceResult.Success).egaisId)
        assertEquals(1, result.itemsCount)
        coVerify { repository.acceptIncomingWaybill(xml) }
    }

    @Test
    fun `waybill with zero quantity items flagged as invalid`() = runTest {
        coEvery { repository.acceptIncomingWaybill(any()) } returns
            EgaisResult.Success(egaisId = "WB-OK", message = "OK")

        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-002</ns:Identity>
                <ns:Header><ns:IsAccept>Accepted</ns:IsAccept><ns:Quantity>1</ns:Quantity></ns:Header>
                <ns:Content>
                    <ns:Position>
                        <ns:Product><ns:Name>Вино</ns:Name><ns:Barcode>4607000001</ns:Barcode></ns:Product>
                        <ns:Quantity>0</ns:Quantity>
                        <ns:Price>450.00</ns:Price>
                    </ns:Position>
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Invalid)
        assertTrue((result as WaybillAcceptanceResult.Invalid).errors.any { it.contains("нуль", ignoreCase = true) || it.contains("должно") || it.contains("больше") })
    }

    @Test
    fun `waybill with negative price flagged as invalid`() = runTest {
        coEvery { repository.acceptIncomingWaybill(any()) } returns
            EgaisResult.Success(egaisId = "WB-OK", message = "OK")

        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-003</ns:Identity>
                <ns:Header><ns:IsAccept>Accepted</ns:IsAccept><ns:Quantity>1</ns:Quantity></ns:Header>
                <ns:Content>
                    <ns:Position>
                        <ns:Product><ns:Name>Водка</ns:Name><ns:Barcode>4607000002</ns:Barcode></ns:Product>
                        <ns:Quantity>6</ns:Quantity>
                        <ns:Price>-100.00</ns:Price>
                    </ns:Position>
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Invalid)
        assertTrue((result as WaybillAcceptanceResult.Invalid).errors.any { it.contains("цена", ignoreCase = true) || it.contains("отрицат") })
    }

    @Test
    fun `EGAIS network error returns error result`() = runTest {
        coEvery { repository.acceptIncomingWaybill(any()) } returns
            EgaisResult.Error(code = -1, message = "Сеть недоступна")

        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-004</ns:Identity>
                <ns:Header><ns:IsAccept>Accepted</ns:IsAccept><ns:Quantity>1</ns:Quantity></ns:Header>
                <ns:Content>
                    <ns:Position>
                        <ns:Product><ns:Name>Пиво</ns:Name><ns:Barcode>4607000003</ns:Barcode></ns:Product>
                        <ns:Quantity>12</ns:Quantity>
                        <ns:Price>120.00</ns:Price>
                    </ns:Position>
                </ns:Content>
            </ns:Waybill>
        """.trimIndent()

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Error)
        assertEquals("Сеть недоступна", (result as WaybillAcceptanceResult.Error).message)
    }

    @Test
    fun `waybill with zero items blocked`() = runTest {
        val xml = """
            <ns:Waybill>
                <ns:Identity>WB-2024-005</ns:Identity>
                <ns:Header><ns:IsAccept>Accepted</ns:IsAccept><ns:Quantity>0</ns:Quantity></ns:Header>
                <ns:Content/>
            </ns:Waybill>
        """.trimIndent()

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Invalid)
        assertTrue((result as WaybillAcceptanceResult.Invalid).errors.any { it.contains("позиц", ignoreCase = true) })
    }

    @Test
    fun `malformed XML returns invalid result`() = runTest {
        val xml = "<not-waybill>broken</not-waybill>"

        val result = useCase.acceptWaybill(xml)

        assertTrue(result is WaybillAcceptanceResult.Invalid)
    }
}