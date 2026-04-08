package com.vitbon.kkm.core.features

/**
 * Список optional модулей, активируемых по ключу/флагу.
 * Без переустановки приложения.
 */
enum class FeatureFlag {
    /** Модуль ЕГАИС (алкоголь: УТМ, акты вскрытия, проверка возраста) */
    EGAAIS_ENABLED,

    /** Модуль маркировки Честный ЗНАК (DataMatrix, выбытие) */
    CHASEZNAK_ENABLED,

    /** Модуль эквайринга (платёжные терминалы) */
    ACQUIRING_ENABLED,

    /** Модуль СБП (Система быстрых платежей) */
    SBP_ENABLED
}
