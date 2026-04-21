package com.vitbon.kkm.features.shift.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftUseCaseTest {

    private val fiscalCore = mockk<FiscalCore>()
    private val shiftDao = mockk<ShiftDao>(relaxed = true)
    private val checkDao = mockk<CheckDao>()
    private val useCase = ShiftUseCase(fiscalCore, shiftDao, checkDao)

    @Test
    fun `closeShift aggregates cash and card totals from open shift checks`() = runBlocking {
        val shiftId = "shift-open-1"
        val checks = listOf(
            check(id = "sale-cash-1", shiftId = shiftId, type = "sale", paymentType = "cash", total = 1_000L),
            check(id = "sale-card-1", shiftId = shiftId, type = "sale", paymentType = "card", total = 2_000L),
            check(id = "return-card-1", shiftId = shiftId, type = "return", paymentType = "card", total = 500L),
            check(id = "sale-sbp-1", shiftId = shiftId, type = "sale", paymentType = "sbp", total = 700L)
        )

        coEvery { fiscalCore.closeShift() } returns FiscalResult.Success(
            fiscalSign = "fs-close-1",
            fnNumber = "fn-1",
            fdNumber = "fd-1",
            timestamp = 1_700_000_000_000L
        )
        coEvery { checkDao.findByShiftId(shiftId) } returns checks

        val result = useCase.closeShift(shiftId)

        assertEquals(ShiftResult.Success(shiftId), result)
        coVerify(exactly = 1) { fiscalCore.closeShift() }
        coVerify(exactly = 1) { checkDao.findByShiftId(shiftId) }
        coVerify(exactly = 1) {
            shiftDao.closeShift(
                shiftId = shiftId,
                closedAt = any(),
                totalCash = 1_000L,
                totalCard = 2_000L
            )
        }
    }

    @Test
    fun `closeShift does not update dao when fiscal close fails`() = runBlocking {
        val shiftId = "shift-open-2"

        coEvery { fiscalCore.closeShift() } returns FiscalResult.Error(
            code = 42,
            message = "fiscal close failed"
        )

        val result = useCase.closeShift(shiftId)

        assertEquals(ShiftResult.Error(42, "fiscal close failed"), result)
        coVerify(exactly = 1) { fiscalCore.closeShift() }
        coVerify(exactly = 0) { checkDao.findByShiftId(any()) }
        coVerify(exactly = 0) { shiftDao.closeShift(any(), any(), any(), any()) }
    }

    private fun check(
        id: String,
        shiftId: String,
        type: String,
        paymentType: String?,
        total: Long
    ): LocalCheck = LocalCheck(
        id = id,
        localUuid = "local-$id",
        shiftId = shiftId,
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
