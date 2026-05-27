package com.vitbon.kkm.features.reports.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

class ReportLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Singleton
class ReportsUseCase @Inject constructor(
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao,
    private val shiftDao: ShiftDao,
    private val api: VitbonApi
) {
    suspend fun getMovementReport(period: String, since: Long): MovementReportData {
        val remoteResponse = try {
            api.getMovementReport(period = period, since = since)
        } catch (error: Exception) {
            throw ReportLoadException("Не удалось загрузить отчёт движения", error)
        }

        if (remoteResponse.isSuccessful) {
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

        throw ReportLoadException("Не удалось загрузить отчёт движения")
    }

    suspend fun getSalesReport(period: String, fromTs: Long, toTs: Long): SalesReport {
        val shiftId = if (period == "shift") shiftDao.findOpenShift()?.id else null
        val remoteResponse = try {
            api.getSalesReport(period = period, shiftId = shiftId, since = fromTs)
        } catch (error: Exception) {
            throw ReportLoadException("Не удалось загрузить отчёт", error)
        }

        if (remoteResponse.isSuccessful) {
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
                    },
                    source = ReportDataSource.REMOTE
                )
            }
        }

        throw ReportLoadException("Не удалось загрузить отчёт")
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

enum class ReportDataSource {
    REMOTE,
    LOCAL
}

data class SalesReport(
    val totalSales: Long,     // копейки
    val totalReturns: Long,
    val cashTotal: Long,
    val cardTotal: Long,
    val sbpTotal: Long,
    val checkCount: Int,
    val returnCount: Int,
    val averageCheck: Long,   // копейки
    val topProducts: List<ProductSales> = emptyList(),
    val source: ReportDataSource
)
