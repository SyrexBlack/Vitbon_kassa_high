package com.vitbon.kkm.features.egais.domain

enum class EgaisDocStatus {
    RECEIVED,      // ЕГАИС принял
    REJECTED,      // отклонён
    IN_PROGRESS,   // в обработке
    ERROR
}

enum class EgaisDocType {
    INCOMING,      // Приходная накладная
    WRITE_OFF,      // Списание
    INVENTORY,     // Инвентаризация
    TARA_OPEN,      // Вскрытие тары (акт)
    TARA_CLOSE      // Закрытие тары
}

data class EgaisDoc(
    val id: String,
    val type: EgaisDocType,
    val status: EgaisDocStatus,
    val egaisId: String?,       // ИД документа в ЕГАИС
    val createdAt: Long,
    val details: String?
)

sealed class EgaisResult {
    data class Success(val egaisId: String, val message: String) : EgaisResult()
    data class Error(val code: Int, val message: String) : EgaisResult()
}

/** Результат проверки накладной ЕГАИС */
data class IncomingWaybill(
    val egaisId: String,
    val supplierName: String,
    val date: Long,
    val items: List<WaybillItem>
)

data class WaybillItem(
    val productName: String,
    val volume: Double,     // литры
    val price: Double,
    val barcodes: List<String>
)
