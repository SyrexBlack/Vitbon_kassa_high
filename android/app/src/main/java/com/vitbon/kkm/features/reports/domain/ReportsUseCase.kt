package com.vitbon.kkm.features.reports.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsUseCase @Inject constructor(private val checkDao: CheckDao) {
    suspend fun getSalesReport(fromTs: Long, toTs: Long): SalesReport {
        // В реальности: запрос к БД с фильтрами по дате
        val checks = checkDao.findPendingSync().filter {
            it.createdAt in fromTs..toTs && it.type == "SALE"
        }
        val returns = checkDao.findPendingSync().filter {
            it.createdAt in fromTs..toTs && it.type == "RETURN"
        }
        val cashTotal = checks.filter { it.paymentType == "cash" }.sumOf { it.total }
        val cardTotal = checks.filter { it.paymentType == "card" }.sumOf { it.total }
        val sbpTotal = checks.filter { it.paymentType == "sbp" }.sumOf { it.total }
        return SalesReport(
            totalSales = checks.sumOf { it.total },
            totalReturns = returns.sumOf { it.total },
            cashTotal = cashTotal,
            cardTotal = cardTotal,
            sbpTotal = sbpTotal,
            checkCount = checks.size,
            returnCount = returns.size,
            averageCheck = if (checks.isNotEmpty()) checks.sumOf { it.total } / checks.size else 0L
        )
    }
}

data class SalesReport(
    val totalSales: Long,     // копейки
    val totalReturns: Long,
    val cashTotal: Long,
    val cardTotal: Long,
    val sbpTotal: Long,
    val checkCount: Int,
    val returnCount: Int,
    val averageCheck: Long    // копейки
)
