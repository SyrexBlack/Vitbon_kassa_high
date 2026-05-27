package com.vitbon.kkm.features.egais.domain

/**
 * vitbon-kassa-1rd.1.1: EGAIS UTM connectivity status model.
 *
 * Replaces simple boolean checkUtmAvailable() with a typed state
 * that distinguishes all failure modes so the UI can block the
 * right module with a clear reason.
 */
sealed class UtmStatus {
    /** УТМ настроен и полностью готов к операциям */
    data object Ready : UtmStatus()

    /** УТМ не настроен (не указан хост/порт в настройках) */
    data object NotConfigured : UtmStatus()

    /** УТМ настроен, но не отвечает по сети */
    data class Unreachable(val reason: String) : UtmStatus()

    /** УТМ отвечает, но авторизация отклонена */
    data class AuthError(val message: String) : UtmStatus()

    /** УТМ недоступен по другой причине */
    data class UnknownError(val message: String) : UtmStatus()
}

fun UtmStatus.isOperational(): Boolean = this is UtmStatus.Ready