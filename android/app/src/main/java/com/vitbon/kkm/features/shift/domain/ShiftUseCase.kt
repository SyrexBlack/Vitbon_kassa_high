package com.vitbon.kkm.features.shift.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.*
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalShift
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftUseCase @Inject constructor(
    private val fiscalCore: FiscalCore,
    private val shiftDao: ShiftDao,
    private val checkDao: CheckDao
) {
    /**
     * Алгоритм старта по ТЗ:
     * 1. getStatus() → смена открыта <24ч → OK
     * 2. Смена открыта >24ч → предложить закрыть
     * 3. Смена закрыта → кнопки "Открыть" / "Продолжить"
     * 4. При первом чеке (если закрыта) → автооткрытие
     */
    suspend fun checkShiftStatus(): ShiftStatus {
        val status = fiscalCore.getStatus()
        return when {
            !status.shiftOpen -> ShiftStatus.CLOSED
            status.shiftAgeHours != null && status.shiftAgeHours > 24 -> ShiftStatus.EXPIRED
            else -> ShiftStatus.OPEN
        }
    }

    suspend fun openShift(deviceId: String, cashierId: String): ShiftResult {
        return when (val r = fiscalCore.openShift()) {
            is FiscalResult.Success -> {
                val shift = LocalShift(
                    id = UUID.randomUUID().toString(),
                    cashierId = cashierId,
                    deviceId = deviceId,
                    openedAt = System.currentTimeMillis(),
                    closedAt = null,
                    totalCash = 0L,
                    totalCard = 0L
                )
                shiftDao.insert(shift)
                ShiftResult.Success(shift.id)
            }
            is FiscalResult.Error -> ShiftResult.Error(r.code, r.message)
        }
    }

    suspend fun closeShift(shiftId: String): ShiftResult {
        return when (val r = fiscalCore.closeShift()) {
            is FiscalResult.Success -> {
                val checks = checkDao.findByShiftId(shiftId)
                val sales = checks.filter { it.type.equals("sale", ignoreCase = true) }
                val totalCash = sales
                    .filter { it.paymentType.equals("cash", ignoreCase = true) }
                    .sumOf { it.total }
                val totalCard = sales
                    .filter { it.paymentType.equals("card", ignoreCase = true) }
                    .sumOf { it.total }
                shiftDao.closeShift(shiftId, System.currentTimeMillis(), totalCash, totalCard)
                ShiftResult.Success(shiftId)
            }
            is FiscalResult.Error -> ShiftResult.Error(r.code, r.message)
        }
    }

    suspend fun findOpenShiftId(): String? = shiftDao.findOpenShift()?.id

    suspend fun printXReport(): FiscalResult = fiscalCore.printXReport()
}

enum class ShiftStatus { OPEN, CLOSED, EXPIRED }

sealed class ShiftResult {
    data class Success(val shiftId: String) : ShiftResult()
    data class Error(val code: Int, val message: String) : ShiftResult()
}
