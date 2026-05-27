package com.vitbon.kkm.features.statuses.domain

enum class StatusOperation {
    SALE,
    RETURN,
    CORRECTION,
    CASH_DRAWER,
    SHIFT,
    REPORTS,
    STATUSES,
    EGAIS,
    CHASEZNAK
}

data class StatusOperationDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val warning: String? = null
)

object StatusOperationPolicy {
    fun evaluate(status: SystemStatus, operation: StatusOperation): StatusOperationDecision {
        if (operation == StatusOperation.REPORTS || operation == StatusOperation.STATUSES) {
            return StatusOperationDecision(
                allowed = true,
                warning = buildWarning(status)
            )
        }

        if (status.license == LicenseStatus.EXPIRED) {
            return StatusOperationDecision(
                allowed = false,
                reason = "Лицензия просрочена. Доступны только отчёты и статусы."
            )
        }

        return when (operation) {
            StatusOperation.EGAIS -> evaluateModule(
                moduleStatus = status.egaisModule,
                moduleName = "ЕГАИС",
                status = status
            )

            StatusOperation.CHASEZNAK -> evaluateModule(
                moduleStatus = status.chaseznakModule,
                moduleName = "Честный ЗНАК",
                status = status
            )

            else -> StatusOperationDecision(
                allowed = true,
                warning = buildWarning(status)
            )
        }
    }

    private fun evaluateModule(
        moduleStatus: ModuleStatus,
        moduleName: String,
        status: SystemStatus
    ): StatusOperationDecision {
        return when (moduleStatus) {
            ModuleStatus.INACTIVE -> StatusOperationDecision(
                allowed = false,
                reason = "$moduleName не активирован для этой кассы."
            )

            ModuleStatus.UNAVAILABLE -> StatusOperationDecision(
                allowed = false,
                reason = "$moduleName временно недоступен. Проверьте интернет и облачный сервис."
            )

            ModuleStatus.ACTIVE -> StatusOperationDecision(
                allowed = true,
                warning = buildWarning(status)
            )
        }
    }

    private fun buildWarning(status: SystemStatus): String? {
        return when {
            !status.ofd.connected && status.ofd.pendingChecks > 0 -> {
                "ОФД недоступен, в очереди ${status.ofd.pendingChecks} чеков. Продажи разрешены локально, но требуется восстановить отправку."
            }

            status.internet == ConnectionStatus.LOST -> {
                "Интернет недоступен. Продажи разрешены локально, синхронизация будет выполнена позже."
            }

            status.cloudServer == ServiceStatus.ERROR -> {
                "Облачный сервис недоступен. Продажи разрешены локально, синхронизация будет выполнена позже."
            }

            status.license == LicenseStatus.GRACE_PERIOD -> {
                "Лицензия в grace period. Проверьте связь с сервером лицензий."
            }

            else -> null
        }
    }
}