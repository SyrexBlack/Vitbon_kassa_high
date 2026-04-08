package com.vitbon.kkm.features.licensing.domain

/** Статус лицензии */
sealed class LicenseStatus {
    object Active : LicenseStatus()
    data class GracePeriod(val daysLeft: Int) : LicenseStatus()
    object Expired : LicenseStatus()
    data class Error(val message: String) : LicenseStatus()
}

/** Состояние приложения при проверке лицензии */
sealed class AppBlockingState {
    object Unblocked : AppBlockingState()
    data class Blocked(val reason: String) : AppBlockingState()
}
