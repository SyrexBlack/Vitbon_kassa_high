package com.vitbon.kkm.core.fiscal

import com.vitbon.kkm.core.fiscal.model.*

/**
 * Абстракция фискального ядра ККТ.
 * Реализации: MSPOSKFiscalCore, Neva01FFiscalCore.
 */
interface FiscalCore {

    /**
     * Открыть смену.
     * @throws FiscalException если ФН не зарегистрирован или уже открыта
     */
    suspend fun openShift(): FiscalResult

    /**
     * Напечатать чек продажи.
     * @param check подготовленный фискальный документ
     */
    suspend fun printSale(check: FiscalCheck): FiscalResult

    /**
     * Напечатать чек возврата.
     * @param check чек с типом RETURN; должен содержать ссылку на оригинальный fiscalSign
     */
    suspend fun printReturn(check: FiscalCheck): FiscalResult

    /**
     * Напечатать чек коррекции.
     * @param doc документ коррекции
     */
    suspend fun printCorrection(doc: CorrectionDoc): FiscalResult

    /**
     * Закрыть смену (Z-отчёт).
     */
    suspend fun closeShift(): FiscalResult

    /**
     * Промежуточный отчёт (X-отчёт).
     */
    suspend fun printXReport(): FiscalResult

    /**
     * Внесение наличных.
     * @param amount сумма
     * @param comment примечание (nullable)
     */
    suspend fun cashIn(amount: Money, comment: String?): FiscalResult

    /**
     * Изъятие наличных.
     * @param amount сумма
     * @param comment примечание (nullable)
     */
    suspend fun cashOut(amount: Money, comment: String?): FiscalResult

    /**
     * Текущее состояние ФН и смены.
     */
    suspend fun getStatus(): FiscalStatus

    /**
     * Определить текущую версию ФФД ФН.
     * @return "1.05" или "1.2"
     */
    suspend fun getFFDVersion(): FFDVersion

    /**
     * Инициализация ядра (вызвать при старте приложения).
     */
    suspend fun initialize(): Boolean

    /**
     * Освободить ресурсы (вызвать при остановке приложения).
     */
    suspend fun shutdown()
}

/** Исключение при работе с фискальным ядром */
class FiscalException(
    val errorCode: Int,
    message: String,
    val recoverable: Boolean = false
) : RuntimeException("[$errorCode] $message")
