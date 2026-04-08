package com.vitbon.kkm.core.fiscal.ffd

import com.vitbon.kkm.core.fiscal.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Строитель фискальных документов (ФД) для передачи в ККТ.
 * Поддерживает ФФД 1.05 и ФФД 1.2.
 *
 * ФФД 1.05 — базовый набор тегов (1000-1080)
 * ФФД 1.2   — расширенный набор: теги 1125, 1187, 1008, 1234-1238
 *
 * Формирует TLV-структуру фискального документа.
 */
class FiscalDocumentBuilder(private val version: FFDVersion) {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    /**
     * Построить документ продажи (прихода).
     */
    fun buildSale(
        check: FiscalCheck,
        cashierName: String,
        inn: String?,
        ofdinn: String = "0000000000"
    ): FiscalDocument {
        return FiscalDocument(
            version = version,
            type = DocumentType.SALE,
            fields = buildBaseFields(check, cashierName, inn, ofdinn)
                .plus(if (version == FFDVersion.V1_2) buildFFD12Fields(check) else emptyMap())
        )
    }

    /**
     * Построить документ возврата.
     */
    fun buildReturn(
        check: FiscalCheck,
        originalFiscalSign: String,
        cashierName: String,
        inn: String?,
        ofdinn: String = "0000000000"
    ): FiscalDocument {
        val fields = buildBaseFields(check, cashierName, inn, ofdinn).toMutableMap()
        // Тег 1192 — признак возврата + ссылка на оригинальный ФД
        fields[1192] = "2"  // признак возврата
        fields[1193] = originalFiscalSign  // фискальный признак исходного чека
        return FiscalDocument(
            version = version,
            type = DocumentType.RETURN,
            fields = fields
        )
    }

    /**
     * Построить документ коррекции.
     */
    fun buildCorrection(
        doc: CorrectionDoc,
        cashierName: String,
        inn: String?,
        ofdinn: String = "0000000000"
    ): FiscalDocument {
        val isIncome = doc.type == CheckType.CORRECTION_INCOME
        val fields = mutableMapOf<Int, String>()

        // Теги ФФД 1.05 (базовые)
        fields[1000] = if (isIncome) "1" else "2"  // тип документа: приход/расход
        fields[1030] = "3"  // версия ККТ
        fields[1031] = "1"  // версия ФФД: 1.05 = 1, 1.2 = 2
        fields[1040] = doc.correctionNumber  // номер чека коррекции
        fields[1041] = formatDate(doc.correctionDate)  // дата документа основания
        fields[1042] = formatTime(doc.correctionDate)  // время документа
        fields[1055] = cashierName  // кассир

        if (inn != null) fields[1018] = inn  // ИНН кассира

        // Сумма коррекции (тег 1174) — в копейках
        fields[1174] = (doc.baseSum.kopecks).toString()

        // Разбивка нал/безнал (теги 1215, 1216)
        if (doc.cashSum.kopecks > 0) fields[1215] = doc.cashSum.kopecks.toString()
        if (doc.cardSum.kopecks > 0) fields[1216] = doc.cardSum.kopecks.toString()

        // Основание коррекции (тег 1177)
        fields[1177] = doc.reason

        // НДС (тег 1203)
        fields[1203] = doc.vatRate.tag

        // ФФД 1.2 расширение
        if (version == FFDVersion.V1_2) {
            fields[1187] = "vitbon.ru"  // адрес сайта ФНС
            fields[1192] = "3"  // признак: чек коррекции
            // теги 1234-1238 — разбивка по типам платежа (наличные/безнал)
        }

        return FiscalDocument(
            version = version,
            type = if (isIncome) DocumentType.CORRECTION_INCOME else DocumentType.CORRECTION_EXPENSE,
            fields = fields
        )
    }

    /**
     * Построить документ внесения.
     */
    fun buildCashIn(amount: Money, comment: String?, cashierName: String, inn: String?): FiscalDocument {
        val fields = mutableMapOf<Int, String>()
        fields[1000] = "5"  // тип документа: внесение
        fields[1031] = if (version == FFDVersion.V1_2) "2" else "1"
        fields[1055] = cashierName
        if (inn != null) fields[1018] = inn
        fields[1174] = amount.kopecks.toString()
        fields[1036] = formatDateTime(System.currentTimeMillis())
        if (comment != null) fields[1037] = comment
        if (version == FFDVersion.V1_2) fields[1187] = "vitbon.ru"
        return FiscalDocument(version = version, type = DocumentType.CASH_IN, fields = fields)
    }

    /**
     * Построить документ изъятия.
     */
    fun buildCashOut(amount: Money, comment: String?, cashierName: String, inn: String?): FiscalDocument {
        val fields = mutableMapOf<Int, String>()
        fields[1000] = "6"  // тип: изъятие
        fields[1031] = if (version == FFDVersion.V1_2) "2" else "1"
        fields[1055] = cashierName
        if (inn != null) fields[1018] = inn
        fields[1174] = amount.kopecks.toString()
        fields[1036] = formatDateTime(System.currentTimeMillis())
        if (comment != null) fields[1037] = comment
        if (version == FFDVersion.V1_2) fields[1187] = "vitbon.ru"
        return FiscalDocument(version = version, type = DocumentType.CASH_OUT, fields = fields)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun buildBaseFields(
        check: FiscalCheck,
        cashierName: String,
        inn: String?,
        ofdinn: String
    ): Map<Int, String> {
        val fields = mutableMapOf<Int, String>()

        // 1000 — тип документа (1=приход, 2=возврат прихода, 3=расход, 4=возврат расхода)
        fields[1000] = when (check.type) {
            CheckType.SALE -> "1"
            CheckType.RETURN -> "2"
            CheckType.CORRECTION_INCOME -> "1"
            CheckType.CORRECTION_EXPENSE -> "3"
            else -> "1"
        }
        fields[1030] = "3"  // версия ККТ
        fields[1031] = if (version == FFDVersion.V1_2) "2" else "1"  // версия ФФД
        fields[1055] = cashierName
        if (inn != null) fields[1018] = inn
        fields[1036] = formatDateTime(check.items.firstOrNull()?.let { System.currentTimeMillis() } ?: System.currentTimeMillis())

        // Оплата (теги 1030 нет, это в 1214)
        fields[1214] = check.total.kopecks.toString()  // сумма расчёта

        // 1215 — наличные, 1216 — электронными
        check.payments.forEach { payment ->
            when (payment.type) {
                PaymentType.CASH -> fields[1215] = payment.amount.kopecks.toString()
                PaymentType.CARD -> fields[1216] = payment.amount.kopecks.toString()
                PaymentType.SBP -> fields[1216] = (fields[1216]?.toLongOrNull() ?: 0L + payment.amount.kopecks).toString()
                PaymentType.BONUS -> fields[1216] = (fields[1216]?.toLongOrNull() ?: 0L + payment.amount.kopecks).toString()
                PaymentType.MIXED -> {} // уже разбито в payments
            }
        }

        // Позиции чека (теги 1059-1065 для каждого товара)
        check.items.forEachIndexed { index, item ->
            val baseTag = 1059 + index * 4
            fields[baseTag] = item.name  // 1059, 1063, 1067... — наименование
            fields[baseTag + 1] = item.quantity.toString()  // кол-во
            fields[baseTag + 2] = item.price.kopecks.toString()  // цена за единицу
            fields[baseTag + 3] = item.total.kopecks.toString()  // стоимость
        }

        // 1140 — признак способа расчёта (1=полная, 2=частичная и т.д.)
        fields[1140] = "1"

        return fields
    }

    /** ФФД 1.2 расширенные теги */
    private fun buildFFD12Fields(check: FiscalCheck): Map<Int, String> {
        val fields = mutableMapOf<Int, String>()
        // 1125 — признак интернет-расчёта (если applicable)
        // fields[1125] = "1" // если продажа через интернет
        fields[1187] = "vitbon.ru"  // адрес сайта ФНС
        if (check.customerPhone != null) fields[1008] = check.customerPhone
        if (check.customerEmail != null) fields[1008] = check.customerEmail

        // 1234-1238 — разбивка платежей при split payment
        check.payments.forEach { payment ->
            when (payment.type) {
                PaymentType.CASH -> fields[1234] = payment.amount.kopecks.toString()
                PaymentType.CARD -> fields[1235] = payment.amount.kopecks.toString()
                PaymentType.SBP -> fields[1236] = payment.amount.kopecks.toString()
                PaymentType.BONUS -> fields[1237] = payment.amount.kopecks.toString()
                PaymentType.MIXED -> {} // handle individually
            }
        }
        return fields
    }

    private fun formatDateTime(ts: Long): String {
        return dateFormat.format(Date(ts))
    }

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))
    }

    private fun formatTime(ts: Long): String {
        return SimpleDateFormat("HHmmss", Locale.US).format(Date(ts))
    }
}
