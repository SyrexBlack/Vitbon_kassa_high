package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.TaxSystem
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakRepository
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakValidation
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.products.domain.ProductRepository
import com.vitbon.kkm.features.sales.domain.Cart
import com.vitbon.kkm.features.sales.domain.CartItem
import com.vitbon.kkm.features.sales.domain.MarkedGoodsSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED — vitbon-kassa-1rd.2.2: SBP QR payment lifecycle.
 * Fiscal sale only after SBP confirmation succeeds.
 */
class SbpPaymentIntegrationTest {

    private val sbpService = mockk<SbpPaymentService>(relaxed = true)
    private val fiscalOrchestrator = mockk<FiscalOperationOrchestrator>(relaxed = true)
    private val checkDao = mockk<com.vitbon.kkm.data.local.dao.CheckDao>(relaxed = true)
    private val checkItemDao = mockk<com.vitbon.kkm.data.local.dao.CheckItemDao>(relaxed = true)
    private val shiftDao = mockk<com.vitbon.kkm.data.local.dao.ShiftDao>(relaxed = true)
    private val productRepository = mockk<ProductRepository>(relaxed = true)
    private val chaseznakRepository = mockk<ChaseznakRepository>(relaxed = true)

    init {
        coEvery { fiscalOrchestrator.executeSale(any()) } returns
            FiscalRuntimeResult.Success(
                fiscalSign = "FS${System.currentTimeMillis()}",
                fnNumber = "FN001", fdNumber = "FD001", ffdVersion = "1.2"
            )
        coEvery { fiscalOrchestrator.executeStatusCheck() } returns mockk(relaxed = true)
        coEvery { chaseznakRepository.validateCode(any()) } returns
            ChaseznakValidation("code", ChaseznakStatus.OK, null, null, null)
    }

    private val processSaleUseCase = com.vitbon.kkm.features.sales.domain.ProcessSaleUseCase(
        fiscalOrchestrator = fiscalOrchestrator,
        checkDao = checkDao,
        checkItemDao = checkItemDao,
        shiftDao = shiftDao,
        fiscalConfig = FiscalConfig(taxSystem = TaxSystem.OSN, orgInn = null)
    )

    private val markedGoodsSaleUseCase = MarkedGoodsSaleUseCase(
        chaseznakRepository = chaseznakRepository,
        productRepository = productRepository,
        innerUseCase = processSaleUseCase
    )

    private val saleWithSbp = SaleWithSbpUseCase(sbpService, markedGoodsSaleUseCase)

    private val sbpCart = Cart(
        items = listOf(
            CartItem(
                productId = "p-1", barcode = "4607001234567", name = "Вода",
                quantity = 1.0, price = Money(200_00L),
                discount = Money.ZERO, vatRate = VatRate.VAT_22
            )
        ),
        paymentType = PaymentType.SBP
    )

    @Test
    fun `SBP confirmed then fiscal sale proceeds`() = runTest {
        val txId = "tx-sbp-001"
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Created(qrData = "https://qr.sbp.ru/tx$txId", transactionId = txId, expiresAt = System.currentTimeMillis() + 300_000)
        coEvery { sbpService.pollConfirmation(txId) } returns SbpResult.Confirmed(transactionId = txId)
        coEvery { sbpService.cancelQr(txId) } returns true

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.Success)
        assertTrue((result as SaleResult.Success).fiscalSign.isNotBlank())
    }

    @Test
    fun `SBP declined prevents fiscal sale and surfaces error`() = runTest {
        val txId = "tx-sbp-decline"
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Created(qrData = "https://qr.sbp.ru/tx$txId", transactionId = txId, expiresAt = System.currentTimeMillis() + 300_000)
        coEvery { sbpService.pollConfirmation(txId) } returns SbpResult.Declined(reason = "INSUFFICIENT_FUNDS")

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("SBP_DECLINED", (result as SaleResult.TerminalError).reason)
    }

    @Test
    fun `SBP timeout prevents fiscal sale and cancels QR`() = runTest {
        val txId = "tx-sbp-timeout"
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Created(qrData = "https://qr.sbp.ru/tx$txId", transactionId = txId, expiresAt = System.currentTimeMillis() + 300_000)
        coEvery { sbpService.pollConfirmation(txId) } returns SbpResult.Timeout
        coEvery { sbpService.cancelQr(txId) } returns true

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertTrue((result as SaleResult.TerminalError).message.contains("5 минут", ignoreCase = true))
        coVerify { sbpService.cancelQr(txId) }
    }

    @Test
    fun `QR creation failed prevents fiscal sale and surfaces error`() = runTest {
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Failed(reason = "Bank unavailable")

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("SBP_QR_FAILED", (result as SaleResult.TerminalError).reason)
        assertEquals("Bank unavailable", (result as SaleResult.TerminalError).message)
    }

    @Test
    fun `QR expired prevents fiscal sale`() = runTest {
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Expired(transactionId = "tx-expired")

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("SBP_QR_EXPIRED", (result as SaleResult.TerminalError).reason)
    }

    @Test
    fun `cash payment skips SBP and goes directly to fiscal`() = runTest {
        val cashCart = sbpCart.copy(paymentType = PaymentType.CASH)

        val result = saleWithSbp.execute(cashCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.Success)
    }

    @Test
    fun `onQrCreated callback fires when QR is created`() = runTest {
        val txId = "tx-cb-001"
        var callbackFired = false
        var capturedQr = ""
        var capturedTxId = ""
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Created(qrData = "https://qr.sbp.ru/tx$txId", transactionId = txId, expiresAt = System.currentTimeMillis() + 300_000)
        coEvery { sbpService.pollConfirmation(txId) } returns SbpResult.Confirmed(transactionId = txId)

        saleWithSbp.execute(
            sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false,
            onQrCreated = { qr, tid ->
                callbackFired = true
                capturedQr = qr
                capturedTxId = tid
            }
        )

        assertTrue(callbackFired)
        assertTrue(capturedQr.contains(txId))
        assertEquals(txId, capturedTxId)
    }

    @Test
    fun `SBP comm error prevents fiscal sale`() = runTest {
        val txId = "tx-comm-err"
        coEvery { sbpService.createQr(any()) } returns
            SbpQrState.Created(qrData = "https://qr.sbp.ru/tx$txId", transactionId = txId, expiresAt = System.currentTimeMillis() + 300_000)
        coEvery { sbpService.pollConfirmation(txId) } returns
            SbpResult.CommunicationError(message = "Connection refused")

        val result = saleWithSbp.execute(sbpCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("SBP_COMM_ERROR", (result as SaleResult.TerminalError).reason)
    }
}
