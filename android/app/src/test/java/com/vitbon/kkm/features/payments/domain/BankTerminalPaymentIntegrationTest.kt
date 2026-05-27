package com.vitbon.kkm.features.payments.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.fiscal.runtime.FiscalRuntimeResult
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.TaxSystem
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakRepository
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakValidation
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.products.domain.ProductRepository
import com.vitbon.kkm.features.sales.domain.Cart
import com.vitbon.kkm.features.sales.domain.CartItem
import com.vitbon.kkm.features.sales.domain.MarkedGoodsSaleUseCase
import com.vitbon.kkm.features.sales.domain.ProcessSaleUseCase
import com.vitbon.kkm.features.sales.domain.SaleResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED — vitbon-kassa-1rd.2.1: bank terminal payment lifecycle.
 * Fiscal sale is only created AFTER terminal approval succeeds.
 */
class BankTerminalPaymentIntegrationTest {

    private val terminalService = mockk<BankTerminalService>(relaxed = true)
    private val fiscalOrchestrator = mockk<FiscalOperationOrchestrator>(relaxed = true)
    private val checkDao = mockk<com.vitbon.kkm.data.local.dao.CheckDao>(relaxed = true)
    private val checkItemDao = mockk<com.vitbon.kkm.data.local.dao.CheckItemDao>(relaxed = true)
    private val shiftDao = mockk<com.vitbon.kkm.data.local.dao.ShiftDao>(relaxed = true)
    private val productRepository = mockk<ProductRepository>(relaxed = true)
    private val chaseznakRepository = mockk<ChaseznakRepository>(relaxed = true)

    init {
        // Stub fiscal orchestrator to return success for all sale operations
        coEvery { fiscalOrchestrator.executeSale(any()) } returns
            FiscalRuntimeResult.Success(
                fiscalSign = "FS${System.currentTimeMillis()}",
                fnNumber = "FN001",
                fdNumber = "FD001",
                ffdVersion = "1.2"
            )
        coEvery { fiscalOrchestrator.executeStatusCheck() } returns mockk(relaxed = true)
        // Stub chaseznak to allow all codes (no validation blocking in terminal tests)
        coEvery { chaseznakRepository.validateCode(any()) } returns
            ChaseznakValidation("code", ChaseznakStatus.OK, null, null, null)
    }

    private val processSaleUseCase = ProcessSaleUseCase(
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

    private val saleWithTerminal = SaleWithTerminalUseCase(terminalService, markedGoodsSaleUseCase)

    private val testCart = Cart(
        items = listOf(
            CartItem(
                productId = "p-1",
                barcode = "4607001234567",
                name = "Вода",
                quantity = 1.0,
                price = Money(150_00L),
                discount = Money.ZERO,
                vatRate = VatRate.VAT_22
            )
        ),
        paymentType = PaymentType.CARD
    )

    @Test
    fun `terminal approval succeeds then fiscal sale proceeds`() = runTest {
        val approvalCode = "AP${System.currentTimeMillis()}"
        coEvery { terminalService.approvePayment(any(), PaymentType.CARD) } returns
            TerminalResult.Success(approvalCode = approvalCode)

        val result = saleWithTerminal.execute(testCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.Success)
        assertTrue((result as SaleResult.Success).fiscalSign.isNotBlank())
    }

    @Test
    fun `terminal decline prevents fiscal sale and surfaces error`() = runTest {
        coEvery { terminalService.approvePayment(any(), PaymentType.CARD) } returns
            TerminalResult.Declined(reason = "INSUFFICIENT_FUNDS")

        val result = saleWithTerminal.execute(testCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("INSUFFICIENT_FUNDS", (result as SaleResult.TerminalError).reason)
    }

    @Test
    fun `terminal timeout prevents fiscal sale and surfaces error`() = runTest {
        coEvery { terminalService.approvePayment(any(), PaymentType.CARD) } returns
            TerminalResult.Timeout

        val result = saleWithTerminal.execute(testCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertTrue((result as SaleResult.TerminalError).message.contains("timed out", ignoreCase = true))
    }

    @Test
    fun `terminal comm error prevents fiscal sale and surfaces error`() = runTest {
        coEvery { terminalService.approvePayment(any(), PaymentType.CARD) } returns
            TerminalResult.CommunicationError(message = "Connection refused")

        val result = saleWithTerminal.execute(testCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.TerminalError)
        assertEquals("Connection refused", (result as SaleResult.TerminalError).message)
    }

    @Test
    fun `cash payment skips terminal and goes directly to fiscal`() = runTest {
        val cashCart = testCart.copy(paymentType = PaymentType.CASH)
        // No terminal call for CASH — fiscal directly
        val result = saleWithTerminal.execute(cashCart, "cashier-1", "device-1", "shift-1", CashierRole.CASHIER, false)

        assertTrue(result is SaleResult.Success)
    }
}