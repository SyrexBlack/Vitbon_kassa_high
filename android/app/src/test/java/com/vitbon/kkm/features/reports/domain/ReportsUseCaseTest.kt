package com.vitbon.kkm.features.reports.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsUseCaseTest {

    private val checkDao = mockk<CheckDao>()
    private val useCase = ReportsUseCase(checkDao)

    @Test
    fun `getSalesReport aggregates sales and returns from dao range`() = runBlocking {
        val fromTs = 1_000L
        val toTs = 9_999L

        coEvery { checkDao.findByDateRange(fromTs, toTs) } returns listOf(
            check(id = "sale-cash", type = "sale", paymentType = "cash", total = 1_000L),
            check(id = "sale-card", type = "sale", paymentType = "card", total = 2_000L),
            check(id = "sale-sbp", type = "sale", paymentType = "sbp", total = 3_000L),
            check(id = "return-card", type = "return", paymentType = "card", total = 500L)
        )

        val report = useCase.getSalesReport(fromTs, toTs)

        coVerify(exactly = 1) { checkDao.findByDateRange(fromTs, toTs) }
        assertEquals(6_000L, report.totalSales)
        assertEquals(500L, report.totalReturns)
        assertEquals(1_000L, report.cashTotal)
        assertEquals(2_000L, report.cardTotal)
        assertEquals(3_000L, report.sbpTotal)
        assertEquals(3, report.checkCount)
        assertEquals(1, report.returnCount)
        assertEquals(2_000L, report.averageCheck)
    }

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
}
