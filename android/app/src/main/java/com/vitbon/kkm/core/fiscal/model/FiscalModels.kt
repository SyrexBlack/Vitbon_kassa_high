package com.vitbon.kkm.core.fiscal.model

import java.io.Serializable

/**
 * Денежная сумма — всегда в копейках (Long) для избежания floating-point ошибок.
 */
@JvmInline
value class Money(val kopecks: Long) : Serializable {
    operator fun plus(other: Money) = Money(kopecks + other.kopecks)
    operator fun minus(other: Money) = Money(kopecks - other.kopecks)
    operator fun times(factor: Double) = Money((kopecks * factor).toLong())
    val rubles: Double get() = kopecks / 100.0

    companion object {
        val ZERO = Money(0L)
        fun fromRubles(r: Double) = Money((r * 100).toLong())
    }
}

/** Ставка НДС согласно ФФД */
enum class VatRate(val tag: String, val displayName: String) {
    VAT_22("1220", "22%"),
    VAT_10("1100", "10%"),
    VAT_0("1030", "0%"),
    VAT_5("1050", "5%"),
    VAT_7("1070", "7%"),
    NO_VAT("no_vat", "БЕЗ НДС")
}

/** Версия формата фискальных документов */
enum class FFDVersion(val displayName: String) {
    V1_05("1.05"),
    V1_2("1.2");

    companion object {
        fun fromString(s: String?): FFDVersion = when (s?.replace(".", "_")) {
            "1_05", "1.05", "V1_05" -> V1_05
            "1_2", "1.2", "V1_2" -> V1_2
            else -> V1_05 // разумный default
        }
    }
}

/** Тип чека */
enum class CheckType(val value: String) {
    SALE("sale"),
    RETURN("return"),
    CORRECTION_INCOME("correction_income"),
    CORRECTION_EXPENSE("correction_expense"),
    CASH_IN("cash_in"),
    CASH_OUT("cash_out")
}

/** Тип оплаты */
enum class PaymentType(val value: String) {
    CASH("cash"),
    CARD("card"),
    SBP("sbp"),
    BONUS("bonus"),
    MIXED("mixed")
}

/** Статус фискальной операции */
sealed class FiscalResult : Serializable {
    data class Success(
        val fiscalSign: String,
        val fnNumber: String,
        val fdNumber: String,
        val timestamp: Long
    ) : FiscalResult()

    data class Error(
        val code: Int,
        val message: String,
        val recoverable: Boolean = false
    ) : FiscalResult()
}

/** Состояние ФН / смены */
data class FiscalStatus(
    val fnRegistered: Boolean,
    val fnNumber: String?,
    val shiftOpen: Boolean,
    val shiftAgeHours: Long?,  // null если смена закрыта
    val currentFdNumber: Int,
    val ofdConnected: Boolean,
    val lastError: String?
)

/** Позиция в чеке */
data class CheckItem(
    val id: String,
    val productId: String?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val price: Money,
    val discount: Money = Money.ZERO,
    val vatRate: VatRate,
    val total: Money = Money.ZERO
) {
    init {
        require(quantity > 0) { "quantity must be > 0" }
        require(price.kopecks >= 0) { "price must be >= 0" }
    }
}

/** Позиция оплаты (split payment) */
data class PaymentLine(
    val type: PaymentType,
    val amount: Money,
    val label: String
)

/** Фискальный чек (приход) */
data class FiscalCheck(
    val id: String,
    val type: CheckType,
    val items: List<CheckItem>,
    val payments: List<PaymentLine>,
    val customerPhone: String? = null,
    val customerEmail: String? = null,
    val agentPhone: String? = null,
    val senderPhone: String? = null,
    val inn: String? = null,
    val additionalInfo: Map<String, String> = emptyMap()
) {
    val total: Money get() = items.fold(Money.ZERO) { acc, item -> acc + item.total }
    val totalDiscount: Money get() = items.fold(Money.ZERO) { acc, item -> acc + item.discount }
}

/** Документ коррекции */
data class CorrectionDoc(
    val id: String,
    val type: CheckType,  // CORRECTION_INCOME or CORRECTION_EXPENSE
    val baseSum: Money,
    val cashSum: Money,
    val cardSum: Money,
    val reason: String,  // текстовое описание ошибки
    val correctionNumber: String,
    val correctionDate: Long,  // дата документа основания (timestamp)
    val vatRate: VatRate
)

/** Результат верификации возраста (Цифровой ID Max) */
data class AgeVerificationResult(
    val verificationId: String,
    val confirmed: Boolean,
    val timestamp: Long,
    val errorMessage: String? = null
)
