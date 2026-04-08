package com.vitbon.kkm.features.chaseznak.domain

enum class ChaseznakStatus {
    OK,           // код в обороте, можно продавать
    NOT_IN_CIRCULATION,  // не в обороте
    ALREADY_SOLD,   // уже выбыл
    EXPIRED,        // срок годности истёк
    ERROR           // ошибка валидации
}

data class ChaseznakValidation(
    val barcode: String,
    val status: ChaseznakStatus,
    val productName: String?,
    val expiryDate: Long?,
    val message: String?
)

sealed class ChaseznakResult {
    data class Success(val code: String) : ChaseznakResult()
    data class Error(val status: ChaseznakStatus, val message: String) : ChaseznakResult()
}
