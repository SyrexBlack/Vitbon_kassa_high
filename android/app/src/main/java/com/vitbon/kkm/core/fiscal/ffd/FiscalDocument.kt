package com.vitbon.kkm.core.fiscal.ffd

import com.vitbon.kkm.core.fiscal.model.FFDVersion

/**
 * Готовый фискальный документ — набор тег→значение.
 * Передаётся в FiscalCore для отправки в ФН.
 */
data class FiscalDocument(
    val version: FFDVersion,
    val type: DocumentType,
    /** Тег (Int) → значение (String). TLV-структура. */
    val fields: Map<Int, String>
) {
    /**
     * Преобразовать в JSON для отладки / передачи в SDK.
     */
    fun toJson(): String {
        return buildString {
            append("{")
            append("\"version\":\"${version.displayName}\",")
            append("\"type\":\"${type.name}\",")
            append("\"fields\":{")
            fields.entries.forEachIndexed { index, (tag, value) ->
                append("\"$tag\":\"${value.replace("\"", "\\\"")}\"")
                if (index < fields.size - 1) append(",")
            }
            append("}}")
        }
    }
}

enum class DocumentType {
    SALE,
    RETURN,
    CORRECTION_INCOME,
    CORRECTION_EXPENSE,
    CASH_IN,
    CASH_OUT
}
