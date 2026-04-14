package com.vitbon.kkm.features.reports.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsUseCase @Inject constructor(
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao,
    private val shiftDao: ShiftDao,
    private val api: VitbonApi
) {
    suspend fun getMovementReport(period: String, since: Long): MovementReportData {
        val remoteResponse = runCatching {
            api.getMovementReport(period = period, since = since)
        }.getOrNull()

        if (remoteResponse?.isSuccessful == true) {
            val body = remoteResponse.body()
            if (body != null) {
                return MovementReportData(
                    openingStock = body.openingStock,
                    income = body.income,
                    sales = body.sales,
                    returns = body.returns,
                    writeoff = body.writeoff,
                    closingStock = body.closingStock,
                    items = body.items.map {
                        MovementItemData(
                            name = it.name,
                            income = it.income,
                            sales = it.sales,
                            balance = it.balance
                        )
                    }
                )
            }
        }

        return MovementReportData(
            openingStock = 0.0,
            income = 0.0,
            sales = 0.0,
            returns = 0.0,
            writeoff = 0.0,
            closingStock = 0.0,
            items = emptyList()
        )
    }

    suspend fun getSalesReport(period: String, fromTs: Long, toTs: Long): SalesReport {
        val shiftId = if (period == "shift") shiftDao.findOpenShift()?.id else null
        val remoteResponse = runCatching {
            api.getSalesReport(period = period, shiftId = shiftId, since = fromTs)
        }.getOrNull()

        if (remoteResponse?.isSuccessful == true) {
            val body = remoteResponse.body()
            if (body != null) {
                return SalesReport(
                    totalSales = body.totalRevenue,
                    totalReturns = body.totalReturns,
                    cashTotal = body.cashRevenue,
                    cardTotal = body.cardRevenue,
                    sbpTotal = 0L,
                    checkCount = body.totalChecks,
                    returnCount = body.returnChecks,
                    averageCheck = body.averageCheck,
                    topProducts = body.topProducts.orEmpty().map {
                        ProductSales(
                            name = it.name,
                            quantity = it.quantity,
                            total = it.total
                        )
                    }
                )
            }
        }

        val checksInRange = checkDao.findByDateRange(fromTs, toTs)
        val checks = checksInRange.filter { it.type.equals("sale", ignoreCase = true) }
        val returns = checksInRange.filter { it.type.equals("return", ignoreCase = true) }
        val cashTotal = checks.filter { it.paymentType.equals("cash", ignoreCase = true) }.sumOf { it.total }
        val cardTotal = checks.filter { it.paymentType.equals("card", ignoreCase = true) }.sumOf { it.total }
        val sbpTotal = checks.filter { it.paymentType.equals("sbp", ignoreCase = true) }.sumOf { it.total }

        val topProducts = checks
            .flatMap { check -> checkItemDao.findByCheckId(check.id) }
            .groupBy { it.name }
            .map { (name, items) ->
                ProductSales(
                    name = name,
                    quantity = items.sumOf { it.quantity },
                    total = items.sumOf { it.total }
                )
            }
            .sortedByDescending { it.total }

        return SalesReport(
            totalSales = checks.sumOf { it.total },
            totalReturns = returns.sumOf { it.total },
            cashTotal = cashTotal,
            cardTotal = cardTotal,
            sbpTotal = sbpTotal,
            checkCount = checks.size,
            returnCount = returns.size,
            averageCheck = if (checks.isNotEmpty()) checks.sumOf { it.total } / checks.size else 0L,
            topProducts = topProducts
        )
    }
}

data class MovementItemData(
    val name: String,
    val income: Double,
    val sales: Double,
    val balance: Double
)

data class MovementReportData(
    val openingStock: Double,
    val income: Double,
    val sales: Double,
    val returns: Double,
    val writeoff: Double,
    val closingStock: Double,
    val items: List<MovementItemData> = emptyList()
)

data class ProductSales(
    val name: String,
    val quantity: Double,
    val total: Long
)

data class SalesReport(
    val totalSales: Long,     // копейки
    val totalReturns: Long,
    val cashTotal: Long,
    val cardTotal: Long,
    val sbpTotal: Long,
    val checkCount: Int,
    val returnCount: Int,
    val averageCheck: Long,   // копейки
    val topProducts: List<ProductSales> = emptyList()
)
