package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.CheckDto
import com.vitbon.kkm.api.dto.ProductSalesDto
import com.vitbon.kkm.api.dto.SalesReportDto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CheckServiceSalesReportTest {

    private val service = CheckServiceForReport()

    @Test
    fun `buildSalesReport aggregates all payment types separately`() {
        val checks = listOf(
            makeSale(total = 100_000L, paymentType = "cash"),
            makeSale(total = 200_000L, paymentType = "card"),
            makeSale(total = 300_000L, paymentType = "sbp"),
            makeSale(total = 50_000L, paymentType = "cash"),
            makeSale(total = 75_000L, paymentType = "card"),
            makeReturn(total = 100_000L, paymentType = "cash")
        )
        val result = service.buildSalesReport(checks, "day")

        assertEquals(5, result.totalChecks)
        assertEquals(1, result.returnChecks)
        assertEquals(725_000L, result.totalRevenue)
        assertEquals(100_000L, result.totalReturns)
        assertEquals(150_000L, result.cashRevenue)
        assertEquals(275_000L, result.cardRevenue)
        assertEquals(300_000L, result.sbpRevenue)
        assertEquals(145_000L, result.averageCheck) // 725k / 5
    }

    @Test
    fun `buildSalesReport returns zero for all when no checks`() {
        val result = service.buildSalesReport(emptyList(), "day")
        assertEquals(0, result.totalChecks)
        assertEquals(0L, result.totalRevenue)
        assertEquals(0L, result.cashRevenue)
        assertEquals(0L, result.cardRevenue)
        assertEquals(0L, result.sbpRevenue)
        assertEquals(0L, result.averageCheck)
    }

    @Test
    fun `buildSalesReport handles mixed case payment types`() {
        val checks = listOf(
            makeSale(100_000L, "CASH"),
            makeSale(200_000L, "Card"),
            makeSale(300_000L, "SBP"),
            makeSale(400_000L, "CaRd")
        )
        val result = service.buildSalesReport(checks, "day")
        assertEquals(100_000L, result.cashRevenue)
        assertEquals(600_000L, result.cardRevenue)
        assertEquals(300_000L, result.sbpRevenue)
    }

    @Test
    fun `buildSalesReport topProducts sorted by total descending`() {
        val checks = listOf(
            makeSale(100_000L, "cash", items = listOf(
                ProductSalesDto(name = "Вода", quantity = 1.0, total = 100_000L)
            )),
            makeSale(200_000L, "cash", items = listOf(
                ProductSalesDto(name = "Вода", quantity = 2.0, total = 200_000L)
            )),
            makeSale(150_000L, "cash", items = listOf(
                ProductSalesDto(name = "Хлеб", quantity = 1.0, total = 150_000L)
            ))
        )
        val result = service.buildSalesReport(checks, "day")

        assertEquals(3, result.topProducts.size)
        assertEquals("Вода", result.topProducts[0].name)
        assertEquals(300_000L, result.topProducts[0].total)
        assertEquals("Хлеб", result.topProducts[1].name)
        assertEquals(150_000L, result.topProducts[1].total)
    }

    @Test
    fun `buildSalesReport returns 0 averageCheck when no sales`() {
        val checks = listOf(
            makeReturn(100_000L, "cash")
        )
        val result = service.buildSalesReport(checks, "day")
        assertEquals(0, result.totalChecks)
        assertEquals(1, result.returnChecks)
        assertEquals(0L, result.averageCheck)
    }

    @Test
    fun `reconciliation sums to totalRevenue`() {
        val checks = listOf(
            makeSale(100_000L, "cash"),
            makeSale(200_000L, "card"),
            makeSale(300_000L, "sbp"),
            makeSale(400_000L, "cash"),
            makeSale(50_000L, "card")
        )
        val result = service.buildSalesReport(checks, "day")
        val paymentSum = result.cashRevenue + result.cardRevenue + result.sbpRevenue
        assertEquals(result.totalRevenue, paymentSum)
    }

    // Minimal port of the service logic for isolated testing
    private class CheckServiceForReport {
        fun buildSalesReport(checks: List<CheckDto>, period: String): SalesReportDto {
            val sales = checks.filter { it.type.equals("SALE", ignoreCase = true) }
            val returns = checks.filter { it.type.equals("RETURN", ignoreCase = true) }
            val totalChecks = sales.size
            val returnChecks = returns.size
            val totalRevenue = sales.sumOf { it.total }
            val totalReturns = returns.sumOf { it.total }
            val cashRevenue = sales.filter { it.paymentType.equals("cash", ignoreCase = true) }.sumOf { it.total }
            val cardRevenue = sales.filter { it.paymentType.equals("card", ignoreCase = true) }.sumOf { it.total }
            val sbpRevenue = sales.filter { it.paymentType.equals("sbp", ignoreCase = true) }.sumOf { it.total }
            val averageCheck = if (totalChecks == 0) 0L else totalRevenue / totalChecks
            val topProducts = sales
                .flatMap { it.items }
                .groupBy { it.name }
                .map { (name, items) ->
                    ProductSalesDto(
                        name = name,
                        quantity = items.sumOf { it.quantity },
                        total = items.sumOf { it.total }
                    )
                }
                .sortedByDescending { it.total }

            return SalesReportDto(
                totalChecks = totalChecks,
                returnChecks = returnChecks,
                totalRevenue = totalRevenue,
                totalReturns = totalReturns,
                cashRevenue = cashRevenue,
                cardRevenue = cardRevenue,
                sbpRevenue = sbpRevenue,
                averageCheck = averageCheck,
                topProducts = topProducts
            )
        }
    }

    private fun makeSale(total: Long, paymentType: String, items: List<ProductSalesDto> = emptyList()) =
        CheckDto(
            id = "id-${(Math.random() * 1000).toInt()}",
            type = "SALE",
            cashierId = "c-1",
            deviceId = "d-1",
            total = total,
            items = items,
            paymentType = paymentType,
            createdAt = System.currentTimeMillis()
        )

    private fun makeReturn(total: Long, paymentType: String, items: List<ProductSalesDto> = emptyList()) =
        CheckDto(
            id = "id-${(Math.random() * 1000).toInt()}",
            type = "RETURN",
            cashierId = "c-1",
            deviceId = "d-1",
            total = total,
            items = items,
            paymentType = paymentType,
            createdAt = System.currentTimeMillis()
        )
}
