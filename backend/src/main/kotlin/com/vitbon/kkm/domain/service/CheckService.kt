package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.*
import org.springframework.stereotype.Service

@Service
class CheckService {
    private val syncedChecks = mutableListOf<CheckDto>()

    fun processSync(checks: List<CheckDto>): CheckSyncResponseDto {
        val failed = mutableListOf<FailedCheckDto>()
        syncedChecks.addAll(checks)
        val processed = checks.size
        return CheckSyncResponseDto(processed, failed)
    }

    fun findChecks(shiftId: String?, date: String?, since: Long?): List<CheckDto> {
        var result = syncedChecks.toList()
        if (shiftId != null) {
            result = result.filter { it.shiftId == shiftId }
        }
        if (since != null) {
            result = result.filter { it.createdAt >= since }
        }
        return result
    }

    fun buildSalesReport(checks: List<CheckDto>, period: String): SalesReportDto {
        val sales = checks.filter { it.type == "SALE" }
        val totalChecks = sales.size
        val totalRevenue = sales.sumOf { it.total }
        val cashRevenue = sales.filter { it.paymentType == "cash" }.sumOf { it.total }
        val cardRevenue = sales.filter { it.paymentType == "card" }.sumOf { it.total }
        val averageCheck = if (totalChecks == 0) 0L else totalRevenue / totalChecks

        return SalesReportDto(
            totalChecks = totalChecks,
            totalRevenue = totalRevenue,
            cashRevenue = cashRevenue,
            cardRevenue = cardRevenue,
            averageCheck = averageCheck
        )
    }
}
