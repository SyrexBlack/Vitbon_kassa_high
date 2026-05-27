package com.vitbon.kkm.features.statuses.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusOperationPolicyTest {

    @Test
    fun `expired license blocks sales but keeps reports and statuses available`() {
        val expiredStatus = baseStatus.copy(license = LicenseStatus.EXPIRED)

        val sales = StatusOperationPolicy.evaluate(expiredStatus, StatusOperation.SALE)
        val reports = StatusOperationPolicy.evaluate(expiredStatus, StatusOperation.REPORTS)
        val statuses = StatusOperationPolicy.evaluate(expiredStatus, StatusOperation.STATUSES)

        assertFalse(sales.allowed)
        assertEquals("Лицензия просрочена. Доступны только отчёты и статусы.", sales.reason)
        assertTrue(reports.allowed)
        assertTrue(statuses.allowed)
    }

    @Test
    fun `egais is blocked when module is unavailable`() {
        val unavailableStatus = baseStatus.copy(egaisModule = ModuleStatus.UNAVAILABLE)

        val decision = StatusOperationPolicy.evaluate(unavailableStatus, StatusOperation.EGAIS)

        assertFalse(decision.allowed)
        assertEquals("ЕГАИС временно недоступен. Проверьте интернет и облачный сервис.", decision.reason)
    }

    @Test
    fun `sales stay allowed with warning when ofd queue is degraded`() {
        val degradedStatus = baseStatus.copy(
            ofd = OfdStatus(
                pendingChecks = 4,
                connected = false
            )
        )

        val decision = StatusOperationPolicy.evaluate(degradedStatus, StatusOperation.SALE)

        assertTrue(decision.allowed)
        assertEquals("ОФД недоступен, в очереди 4 чеков. Продажи разрешены локально, но требуется восстановить отправку.", decision.warning)
    }

    private companion object {
        val baseStatus = SystemStatus(
            internet = ConnectionStatus.AVAILABLE,
            cloudServer = ServiceStatus.OK,
            cloudLastSyncMs = 1_000L,
            ofd = OfdStatus(pendingChecks = 0, connected = true),
            chaseznakModule = ModuleStatus.ACTIVE,
            egaisModule = ModuleStatus.ACTIVE,
            license = LicenseStatus.ACTIVE
        )
    }
}