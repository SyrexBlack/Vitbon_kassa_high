package com.vitbon.kkm.features.statuses.domain

/** Состояние интернет-соединения */
enum class ConnectionStatus {
    AVAILABLE,
    LOST,
    UNKNOWN
}

/** Состояние сервиса */
enum class ServiceStatus {
    OK,
    ERROR,
    UNKNOWN
}

/** Состояние оплаченного модуля */
enum class ModuleStatus {
    ACTIVE,     // модуль активирован
    INACTIVE,   // модуль не активирован
    UNAVAILABLE // модуль активирован но УТМ/ЛМ недоступен
}

/** Агрегированный статус системы */
data class SystemStatus(
    val internet: ConnectionStatus,
    val cloudServer: ServiceStatus,
    val cloudLastSyncMs: Long?,      // null если никогда
    val ofd: OfdStatus,
    val chaseznakModule: ModuleStatus,
    val egaisModule: ModuleStatus,
    val license: LicenseStatus
) {
    companion object {
        fun empty() = SystemStatus(
            internet = ConnectionStatus.UNKNOWN,
            cloudServer = ServiceStatus.UNKNOWN,
            cloudLastSyncMs = null,
            ofd = OfdStatus(0, true),
            chaseznakModule = ModuleStatus.INACTIVE,
            egaisModule = ModuleStatus.INACTIVE,
            license = LicenseStatus.ACTIVE
        )
    }
}

/** Статус ОФД */
data class OfdStatus(
    val pendingChecks: Int,   // кол-во чеков в очереди
    val connected: Boolean
)

/** Статус лицензии (упрощённый для UI) */
enum class LicenseStatus {
    ACTIVE,
    GRACE_PERIOD,
    EXPIRED
}
