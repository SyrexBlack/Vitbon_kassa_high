package com.vitbon.kkm.features.reports.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.data.local.entity.LocalCheckItem
import com.vitbon.kkm.data.local.entity.LocalShift
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.MovementReportDto
import com.vitbon.kkm.data.remote.dto.MovementReportItemDto
import com.vitbon.kkm.data.remote.dto.SalesReportDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ReportsUseCaseTest {

    private val checkDao = mockk<CheckDao>()
    private val checkItemDao = mockk<CheckItemDao>()
    private val shiftDao = mockk<ShiftDao>()
    private val api = mockk<VitbonApi>()
    private val useCase = ReportsUseCase(checkDao, checkItemDao, shiftDao, api)

    @Test
    fun `getSalesReport uses backend report when api succeeds`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { api.getSalesReport("day", null, fromTs) } returns Response.success(
            SalesReportDto(
                totalChecks = 2,
                returnChecks = 1,
                totalRevenue = 15_000L,
                totalReturns = 1_500L,
                cashRevenue = 10_000L,
                cardRevenue = 5_000L,
                averageCheck = 7_500L
            )
        )

        val report = useCase.getSalesReport("day", fromTs, toTs)

        coVerify(exactly = 0) { shiftDao.findOpenShift() }
        coVerify(exactly = 1) { api.getSalesReport("day", null, fromTs) }
        coVerify(exactly = 0) { checkDao.findByDateRange(any(), any()) }
        coVerify(exactly = 0) { checkItemDao.findByCheckId(any()) }
        assertEquals(15_000L, report.totalSales)
        assertEquals(1_500L, report.totalReturns)
        assertEquals(10_000L, report.cashTotal)
        assertEquals(5_000L, report.cardTotal)
        assertEquals(0L, report.sbpTotal)
        assertEquals(2, report.checkCount)
        assertEquals(1, report.returnCount)
        assertEquals(7_500L, report.averageCheck)
        assertEquals(ReportDataSource.REMOTE, report.source)
    }

    @Test
    fun `getSalesReport maps sbpRevenue correctly`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { api.getSalesReport("day", null, fromTs) } returns Response.success(
            SalesReportDto(
                totalChecks = 3,
                returnChecks = 0,
                totalRevenue = 60_000L,
                totalReturns = 0L,
                cashRevenue = 20_000L,
                cardRevenue = 30_000L,
                sbpRevenue = 10_000L,
                averageCheck = 20_000L
            )
        )

        val report = useCase.getSalesReport("day", fromTs, toTs)

        assertEquals(20_000L, report.cashTotal)
        assertEquals(30_000L, report.cardTotal)
        assertEquals(10_000L, report.sbpTotal)
        assertEquals(60_000L, report.totalSales)
        assertEquals(20_000L, report.averageCheck)
    }

    @Test
    fun `getSalesReport fails closed when backend responds non-success`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { api.getSalesReport("day", null, fromTs) } returns Response.error(
            500,
            "server error".toResponseBody("text/plain".toMediaType())
        )
        try {
            useCase.getSalesReport("day", fromTs, toTs)
            fail("Expected getSalesReport to fail closed")
        } catch (error: ReportLoadException) {
            assertTrue(error.message!!.contains("не удалось загрузить", ignoreCase = true))
        }

        coVerify(exactly = 0) { shiftDao.findOpenShift() }
        coVerify(exactly = 1) { api.getSalesReport("day", null, fromTs) }
        coVerify(exactly = 0) { checkDao.findByDateRange(fromTs, toTs) }
        coVerify(exactly = 0) { checkItemDao.findByCheckId(any()) }
    }

    @Test
    fun `getSalesReport fails closed when backend request throws`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { api.getSalesReport("day", null, fromTs) } throws IOException("timeout")
        try {
            useCase.getSalesReport("day", fromTs, toTs)
            fail("Expected getSalesReport to fail closed")
        } catch (error: ReportLoadException) {
            assertTrue(error.message!!.contains("не удалось загрузить", ignoreCase = true))
        }

        coVerify(exactly = 0) { shiftDao.findOpenShift() }
        coVerify(exactly = 1) { api.getSalesReport("day", null, fromTs) }
        coVerify(exactly = 0) { checkDao.findByDateRange(any(), any()) }
        coVerify(exactly = 0) { checkItemDao.findByCheckId(any()) }
    }

    @Test
    fun `getSalesReport uses open shift id for shift period`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L
        val openShift = LocalShift(
            id = "shift-open-1",
            cashierId = "cashier-1",
            deviceId = "device-1",
            openedAt = 1L,
            closedAt = null,
            totalCash = 0L,
            totalCard = 0L
        )
        coEvery { shiftDao.findOpenShift() } returns openShift
        coEvery { api.getSalesReport("shift", "shift-open-1", fromTs) } returns Response.success(
            SalesReportDto(
                totalChecks = 1,
                returnChecks = 0,
                totalRevenue = 1_000L,
                totalReturns = 0L,
                cashRevenue = 1_000L,
                cardRevenue = 0L,
                averageCheck = 1_000L
            )
        )

        val report = useCase.getSalesReport("shift", fromTs, toTs)

        coVerify(exactly = 1) { shiftDao.findOpenShift() }
        coVerify(exactly = 1) { api.getSalesReport("shift", "shift-open-1", fromTs) }
        coVerify(exactly = 0) { checkDao.findByDateRange(any(), any()) }
        assertEquals(1_000L, report.totalSales)
        assertEquals(1, report.checkCount)
    }

    @Test
    fun `getMovementReport uses backend report when api succeeds`() = runBlocking {
        val since = 1_000L
        coEvery { api.getMovementReport("day", since) } returns Response.success(
            MovementReportDto(
                openingStock = 0.0,
                income = 15.0,
                sales = 3.0,
                returns = 1.0,
                writeoff = 2.0,
                closingStock = 11.0,
                items = listOf(
                    MovementReportItemDto(name = "Товар X", income = 10.0, sales = 3.0, balance = 8.0),
                    MovementReportItemDto(name = "Товар Y", income = 5.0, sales = 0.0, balance = 3.0)
                )
            )
        )

        val report = useCase.getMovementReport("day", since)

        coVerify(exactly = 1) { api.getMovementReport("day", since) }
        assertEquals(0.0, report.openingStock, 0.0001)
        assertEquals(15.0, report.income, 0.0001)
        assertEquals(3.0, report.sales, 0.0001)
        assertEquals(1.0, report.returns, 0.0001)
        assertEquals(2.0, report.writeoff, 0.0001)
        assertEquals(11.0, report.closingStock, 0.0001)
        assertEquals(2, report.items.size)
        assertEquals("Товар X", report.items[0].name)
        assertEquals(10.0, report.items[0].income, 0.0001)
        assertEquals(3.0, report.items[0].sales, 0.0001)
        assertEquals(8.0, report.items[0].balance, 0.0001)
    }

    @Test
    fun `getMovementReport fails closed when backend request throws`() = runBlocking {
        val since = 1_000L
        coEvery { api.getMovementReport("day", since) } throws IOException("timeout")

        try {
            useCase.getMovementReport("day", since)
            fail("Expected getMovementReport to fail closed")
        } catch (error: ReportLoadException) {
            assertTrue(error.message!!.contains("не удалось загрузить", ignoreCase = true))
        }

        coVerify(exactly = 1) { api.getMovementReport("day", since) }
    }

    private fun fallbackChecks(): List<LocalCheck> = listOf(
        check(id = "sale-cash", type = "sale", paymentType = "cash", total = 1_000L),
        check(id = "sale-card", type = "sale", paymentType = "card", total = 2_000L),
        check(id = "sale-sbp", type = "sale", paymentType = "sbp", total = 3_000L),
        check(id = "return-card", type = "return", paymentType = "card", total = 500L)
    )

    private fun check(
        id: String,
        type: String,
        paymentType: String?,
        total: Long
    ): LocalCheck = LocalCheck(
        id = id,
        localUuid = "local-$id",
        shiftId = "shift-1",
        cashierId = "cashier-1",
        deviceId = "device-1",
        type = type,
        fiscalSign = null,
        ofdResponse = null,
        ffdVersion = "1.2",
        status = "SYNCED",
        subtotal = total,
        discount = 0L,
        total = total,
        taxAmount = 0L,
        paymentType = paymentType,
        createdAt = 1_700_000_000_000L,
        syncedAt = null
    )

    private fun checkItem(
        id: String,
        checkId: String,
        name: String,
        quantity: Double,
        total: Long
    ): LocalCheckItem = LocalCheckItem(
        id = id,
        checkId = checkId,
        productId = null,
        barcode = null,
        name = name,
        quantity = quantity,
        price = total,
        discount = 0L,
        vatRate = "NO_VAT",
        total = total
    )
}
