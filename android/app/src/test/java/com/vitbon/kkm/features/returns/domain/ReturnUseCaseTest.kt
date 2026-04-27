package com.vitbon.kkm.features.returns.domain

import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.entity.LocalCheck
import com.vitbon.kkm.data.local.entity.LocalCheckItem
import com.vitbon.kkm.features.auth.domain.CashierRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnUseCaseTest {

    private val fiscalOrchestrator = mockk<FiscalOperationOrchestrator>()
    private val checkDao = mockk<CheckDao>(relaxed = true)
    private val checkItemDao = mockk<CheckItemDao>(relaxed = true)

    private val useCase = ReturnUseCase(fiscalOrchestrator, checkDao, checkItemDao)

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
    fun `findCheckByQr parses raw payload and looks up by fp`() = runBlocking {
        val saleCheck = localCheck(id = "sale-qr-raw", type = "sale")
        val qrPayload = "t=20260416T101010&s=129.00&fn=9960440502979149&i=100&fp=FAKE-QR-FS&n=1"
        coEvery { checkDao.findLatestSaleByIdentifier("FAKE-QR-FS") } returns saleCheck

        val found = useCase.findCheckByQr(qrPayload)

        assertEquals(saleCheck, found)
        coVerify(exactly = 1) { checkDao.findLatestSaleByIdentifier("FAKE-QR-FS") }
    }

    @Test
    fun `findCheckByQr parses url payload and looks up by fp`() = runBlocking {
        val saleCheck = localCheck(id = "sale-qr-url", type = "sale")
        val qrPayload = "https://check.example/receipt?t=20260416T101010&s=129.00&fn=9960440502979149&i=100&fp=FAKE-QR-URL&n=1"
        coEvery { checkDao.findLatestSaleByIdentifier("FAKE-QR-URL") } returns saleCheck

        val found = useCase.findCheckByQr(qrPayload)

        assertEquals(saleCheck, found)
        coVerify(exactly = 1) { checkDao.findLatestSaleByIdentifier("FAKE-QR-URL") }
    }

    @Test
    fun `findCheckByQr without fp returns null and skips lookup`() = runBlocking {
        val qrPayloadWithoutFp = "t=20260416T101010&s=129.00&fn=9960440502979149&i=100&n=1"

        val found = useCase.findCheckByQr(qrPayloadWithoutFp)

        assertNull(found)
        coVerify(exactly = 0) { checkDao.findLatestSaleByIdentifier(any()) }
    }

    @Test
    fun `findCheckByQr malformed payload returns null and does not crash`() = runBlocking {
        val malformedPayload = "###not-a-qr###"

        val found = useCase.findCheckByQr(malformedPayload)

        assertNull(found)
        coVerify(exactly = 0) { checkDao.findLatestSaleByIdentifier(any()) }
    }

    @Test
    fun `processReturn denies when role is missing`() = runBlocking {
        val sourceCheck = localCheck(id = "sale-src", type = "sale")
        val inputItems = listOf(
            ReturnItem(
                productId = "prod-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 1.0,
                price = Money(129_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )

        val result = useCase.processReturn(
            sourceCheck,
            inputItems,
            cashierId = "cashier-1",
            cashierRole = null,
            emergencySessionActive = false
        )

        assertTrue(result is ReturnResult.FiscalError)
        result as ReturnResult.FiscalError
        assertEquals(-1, result.code)
        assertEquals("Операция запрещена для текущей роли", result.message)
        coVerify(exactly = 0) { fiscalOrchestrator.executeReturn(any()) }
        coVerify(exactly = 0) { checkDao.insert(any()) }
        coVerify(exactly = 0) { checkItemDao.insertAll(any()) }
    }

    @Test
    fun `processReturn denies during emergency session`() = runBlocking {
        val sourceCheck = localCheck(id = "sale-src", type = "sale")
        val inputItems = listOf(
            ReturnItem(
                productId = "prod-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 1.0,
                price = Money(129_00L),
                discount = Money.ZERO,
                vatRate = VatRate.NO_VAT
            )
        )

        val result = useCase.processReturn(
            sourceCheck,
            inputItems,
            cashierId = "cashier-1",
            cashierRole = CashierRole.ADMIN,
            emergencySessionActive = true
        )

        assertTrue(result is ReturnResult.FiscalError)
        result as ReturnResult.FiscalError
        assertEquals(-1, result.code)
        assertEquals("Операция запрещена для текущей роли", result.message)
        coVerify(exactly = 0) { fiscalOrchestrator.executeReturn(any()) }
        coVerify(exactly = 0) { checkDao.insert(any()) }
        coVerify(exactly = 0) { checkItemDao.insertAll(any()) }
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
        coEvery { fiscalOrchestrator.executeReturn(any()) } returns FiscalRuntimeResult.Success(
            fiscalSign = "FS-RET-1",
            fnNumber = "FN-1",
            fdNumber = "FD-1",
            ffdVersion = "1.2"
        )

        val result = useCase.processReturn(
            sourceCheck,
            inputItems,
            cashierId = "cashier-1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        assertTrue(result is ReturnResult.Success)
        val success = result as ReturnResult.Success
        assertEquals("FS-RET-1", success.fiscalSign)

        coVerify(exactly = 1) { fiscalOrchestrator.executeReturn(any()) }
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
        coEvery { fiscalOrchestrator.executeReturn(any()) } returns FiscalRuntimeResult.Error(
            code = "FISCAL_ERROR",
            message = "Ошибка ФН",
            recoverable = false
        )

        val result = useCase.processReturn(
            sourceCheck,
            inputItems,
            cashierId = "cashier-1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        assertTrue(result is ReturnResult.FiscalError)
        val err = result as ReturnResult.FiscalError
        assertEquals(-1, err.code)
        assertEquals("Ошибка ФН", err.message)

        coVerify(exactly = 1) { fiscalOrchestrator.executeReturn(any()) }
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
