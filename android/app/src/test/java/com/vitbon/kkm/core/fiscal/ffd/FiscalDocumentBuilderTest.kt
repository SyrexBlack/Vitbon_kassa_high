package com.vitbon.kkm.core.fiscal.ffd

import com.vitbon.kkm.core.fiscal.model.*
import org.junit.Assert.*
import org.junit.Test

class FiscalDocumentBuilderTest {

    private fun makeTestCheck() = FiscalCheck(
        id = "test-123",
        type = CheckType.SALE,
        items = listOf(
            CheckItem(
                id = "i1", productId = "p1", barcode = "4601234567890",
                name = "Водка 0.5", quantity = 2.0,
                price = Money(9900), vatRate = VatRate.VAT_22,
                total = Money(19800)
            )
        ),
        payments = listOf(
            PaymentLine(PaymentType.CASH, Money(20000), "Наличные"),
            PaymentLine(PaymentType.CARD, Money(0), "Карта")
        )
    )

    @Test
    fun `FFD 105 — sale — builds correct base fields`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildSale(makeTestCheck(), "Иванов И.И.", "770123456789")

        assertEquals(FFDVersion.V1_05, doc.version)
        assertEquals(DocumentType.SALE, doc.type)
        assertEquals("1", doc.fields[1000])           // приход
        assertEquals("1", doc.fields[1031])           // ФФД 1.05
        assertEquals("Иванов И.И.", doc.fields[1055]) // кассир
        assertEquals("770123456789", doc.fields[1018]) // ИНН
        assertEquals("20000", doc.fields[1214])        // итого расчёт
        assertEquals("20000", doc.fields[1215])        // наличные
    }

    @Test
    fun `FFD 12 — sale — includes extended tags`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_2)
        val doc = builder.buildSale(makeTestCheck(), "Петров П.П.", "770123456789")

        assertEquals("2", doc.fields[1031])          // ФФД 1.2
        assertEquals("vitbon.ru", doc.fields[1187])   // адрес сайта ФНС
        assertNotNull(doc.fields[1234])               // разбивка платежа
    }

    @Test
    fun `return — includes original fiscal sign tag 1193`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildReturn(makeTestCheck(), "ORIG_FISCAL_SIG", "Иванов", null)

        assertEquals("2", doc.fields[1000])        // возврат прихода
        assertEquals("2", doc.fields[1192])        // признак возврата
        assertEquals("ORIG_FISCAL_SIG", doc.fields[1193]) // ссылка на оригинал
    }

    @Test
    fun `correction income — builds correct fields`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildCorrection(
            doc = CorrectionDoc(
                id = "corr-1",
                type = CheckType.CORRECTION_INCOME,
                baseSum = Money(100000),
                cashSum = Money(50000),
                cardSum = Money(50000),
                reason = "Ошибка в сумме чека",
                correctionNumber = "42",
                correctionDate = System.currentTimeMillis(),
                vatRate = VatRate.VAT_22
            ),
            cashierName = "Сидоров С.С.",
            inn = null
        )

        assertEquals("1", doc.fields[1000])         // приход
        assertEquals("42", doc.fields[1040])       // номер коррекции
        assertEquals("100000", doc.fields[1174])   // сумма коррекции
        assertEquals("Ошибка в сумме чека", doc.fields[1177]) // основание
        assertEquals("Сидоров С.С.", doc.fields[1055])
    }

    @Test
    fun `cash in — builds correct fields`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildCashIn(Money(5000), "Внесение выручки", "Кассир", null)

        assertEquals("5", doc.fields[1000])    // внесение
        assertEquals("5000", doc.fields[1174]) // сумма
        assertEquals("Внесение выручки", doc.fields[1037])
    }

    @Test
    fun `cash out — builds correct fields`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildCashOut(Money(3000), "Инкассация", "Кассир", null)

        assertEquals("6", doc.fields[1000])    // изъятие
        assertEquals("3000", doc.fields[1174]) // сумма
        assertEquals("Инкассация", doc.fields[1037])
    }

    @Test
    fun `toJson — produces valid JSON`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
        val doc = builder.buildCashIn(Money(1000), null, "Test", null)
        val json = doc.toJson()

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}}"))
        assertTrue(json.contains("\"version\":\"1.05\""))
        assertTrue(json.contains("\"type\":\"CASH_IN\""))
        assertTrue(json.contains("\"1000\":\"5\""))
    }

    @Test
    fun `FFD 12 correction — includes site tag 1187`() {
        val builder = FiscalDocumentBuilder(FFDVersion.V1_2)
        val doc = builder.buildCorrection(
            CorrectionDoc(
                id = "c1", type = CheckType.CORRECTION_EXPENSE,
                baseSum = Money(5000), cashSum = Money(5000), cardSum = Money.ZERO,
                reason = "Тест", correctionNumber = "1",
                correctionDate = System.currentTimeMillis(),
                vatRate = VatRate.NO_VAT
            ),
            cashierName = "Test", inn = null
        )
        assertEquals("vitbon.ru", doc.fields[1187])
        assertEquals("3", doc.fields[1192])  // чек коррекции
    }
}
