package com.vitbon.kkm.features.returns.domain

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.data.local.entity.LocalCheckItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnUseCaseTest {

    private val fiscalCore = mockk<FiscalCore>()
    private val checkDao = mockk<CheckDao>(relaxed = true)
    private val checkItemDao = mockk<CheckItemDao>(relaxed = true)

    private val useCase = ReturnUseCase(fiscalCore, checkDao, checkItemDao)

    @Test
    fun `findCheckByNumber trims identifier and delegates deterministic lookup`() = runBlocking {
        val saleCheck = localCheck(id = "sale-1", type = "sale")
        coEvery { checkDao.findLatestSaleByIdentifier("sale-1") } returns saleCheck

        val found = useCase.findCheckByNumber("  sale-1  ")

        assertEquals(saleCheck, found)
        coVerify(exactly = 1) { checkDao.findLatestSaleByIdentifier("sale-1") }
    }

    @Test
    fun `findCheckByNumber supports localUuid identifier path`() = runBlocking {
        val saleByLocalUuid = localCheck(id = "sale-local", type = "sale")
        coEvery { checkDao.findLatestSaleByIdentifier("local-sale-local") } returns saleByLocalUuid

        val found = useCase.findCheckByNumber("local-sale-local")

        assertEquals(saleByLocalUuid, found)
        coVerify(exactly = 1) { checkDao.findLatestSaleByIdentifier("local-sale-local") }
    }

    @Test
    fun `findCheckByNumber supports fiscalSign identifier path`() = runBlocking {
        val saleByFiscalSign = localCheck(id = "sale-fs", type = "sale")
        coEvery { checkDao.findLatestSaleByIdentifier("FS-SRC") } returns saleByFiscalSign

        val found = useCase.findCheckByNumber("FS-SRC")

        assertEquals(saleByFiscalSign, found)
        coVerify(exactly = 1) { checkDao.findLatestSaleByIdentifier("FS-SRC") }
    }

    @Test
    fun `processReturn on fiscal success saves return check items and pending sync status with fiscal sign`() = runBlocking {
        val sourceCheck = localCheck(id = "sale-src", type = "sale")
        val inputItems = listOf(
            ReturnItem(
                productId = "prod-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 2.0,
                price = Money(150_00L),
                discount = Money(10_00L),
                vatRate = VatRate.VAT_10
            )
        )
        coEvery { fiscalCore.printReturn(any()) } returns FiscalResult.Success(
            fiscalSign = "FS-RET-1",
            fnNumber = "FN-1",
            fdNumber = "FD-1",
            timestamp = 123L
        )

        val result = useCase.processReturn(sourceCheck, inputItems, cashierId = "cashier-1")

        assertTrue(result is ReturnResult.Success)
        val success = result as ReturnResult.Success
        assertEquals("FS-RET-1", success.fiscalSign)

        coVerify(exactly = 1) { checkDao.insert(match { it.type == "return" && it.id == success.checkId }) }
        coVerify(exactly = 1) {
            checkItemDao.insertAll(match { localItems ->
                localItems.size == 1 &&
                    localItems[0].checkId == success.checkId &&
                    localItems[0].name == "Вода" &&
                    localItems[0].quantity == 2.0 &&
                    localItems[0].price == 150_00L &&
                    localItems[0].discount == 10_00L &&
                    localItems[0].total == 290_00L
            })
        }
        coVerify(exactly = 1) {
            checkDao.updateSyncStatus(
                id = success.checkId,
                status = "PENDING_SYNC",
                fiscalSign = "FS-RET-1",
                ofdResponse = null,
                syncedAt = null
            )
        }
    }

    @Test
    fun `processReturn on fiscal error marks check fiscal error`() = runBlocking {
        val sourceCheck = localCheck(id = "sale-src", type = "sale")
        val inputItems = listOf(
            ReturnItem(
                productId = "prod-2",
                barcode = "4607009990000",
                name = "Сок",
                quantity = 1.0,
                price = Money(99_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )
        coEvery { fiscalCore.printReturn(any()) } returns FiscalResult.Error(
            code = 54,
            message = "Ошибка ФН",
            recoverable = false
        )

        val result = useCase.processReturn(sourceCheck, inputItems, cashierId = "cashier-1")

        assertTrue(result is ReturnResult.FiscalError)
        val err = result as ReturnResult.FiscalError
        assertEquals(54, err.code)
        assertEquals("Ошибка ФН", err.message)

        coVerify(exactly = 1) {
            checkDao.updateSyncStatus(
                id = any(),
                status = "FISCAL_ERROR",
                fiscalSign = null,
                ofdResponse = null,
                syncedAt = null
            )
        }
    }

    private fun localCheck(id: String, type: String): LocalCheck = LocalCheck(
        id = id,
        localUuid = "local-$id",
        shiftId = "shift-1",
        cashierId = "cashier-1",
        deviceId = "device-1",
        type = type,
        fiscalSign = "FS-SRC",
        ofdResponse = null,
        ffdVersion = "1.2",
        status = "SYNCED",
        subtotal = 500_00L,
        discount = 0L,
        total = 500_00L,
        taxAmount = 0L,
        paymentType = PaymentType.CASH.value,
        createdAt = 1_700_000_000_000L,
        syncedAt = 1_700_000_100_000L
    )
}
