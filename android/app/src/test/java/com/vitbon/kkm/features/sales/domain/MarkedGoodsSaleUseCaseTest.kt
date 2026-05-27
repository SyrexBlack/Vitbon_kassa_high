package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakRepository
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakResult
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakValidation
import com.vitbon.kkm.features.products.domain.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.After
import org.junit.Test

class MarkedGoodsSaleUseCaseTest {

    private val chaseznakRepository = mockk<ChaseznakRepository>(relaxed = true)
    private val productRepository = mockk<ProductRepository>(relaxed = true)
    private val innerUseCase = mockk<ProcessSaleUseCase>(relaxed = true)
    private val useCase = MarkedGoodsSaleUseCase(
        chaseznakRepository = chaseznakRepository,
        productRepository = productRepository,
        innerUseCase = innerUseCase
    )

    @Before
    fun mockLog() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(android.util.Log::class)
    }

    // ─── MARKING VALIDATION (existing — preserved) ───────────────────────────

    @Test
    fun `non-marked items bypass validation and call inner use case`() = runTest {
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = "4600000000000", name = "Milk",
                    quantity = 1.0, price = Money(1000), vatRate = VatRate.VAT_22
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.Success(checkId = "ck-1", fiscalSign = "fs", total = 10.0)

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is  SaleResult.Success)
        coVerify(exactly = 0) { chaseznakRepository.validateCode(any()) }
        coVerify { innerUseCase.execute(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `marked items are validated before running inner use case`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.OK,
            productName = "Cigarette Pack", expiryDate = null, message = null
        )
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.Success(checkId = "ck-2", fiscalSign = "fs", total = 20.0)

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.Success)
        coVerify { chaseznakRepository.validateCode(code) }
        coVerify { innerUseCase.execute(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `already sold code blocks sale with appropriate message`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.ALREADY_SOLD,
            productName = "Cigarette Pack", expiryDate = null, message = null
        )

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        assertEquals("CHASENAK_BLOCK", (result as SaleResult.FiscalError).message.take(14))
        coVerify(exactly = 0) { innerUseCase.execute(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `not in circulation code blocks sale`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.NOT_IN_CIRCULATION,
            productName = "Cigarette Pack", expiryDate = null, message = null
        )

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 0) { innerUseCase.execute(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `expired code blocks sale`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.EXPIRED,
            productName = "Cigarette Pack", expiryDate = null, message = null
        )

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
    }

    @Test
    fun `sell is called after fiscal success for each marked code`() = runTest {
        val code1 = "01046123456789052FnS+EqV1XNAmLqT"
        val code2 = "01046123456789053FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code1
                ),
                CartItem(
                    productId = "p2", barcode = null, name = "Cigarettes 2",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code2
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code1) } returns ChaseznakValidation(
            barcode = code1, status = ChaseznakStatus.OK, productName = null, expiryDate = null, message = null
        )
        coEvery { chaseznakRepository.validateCode(code2) } returns ChaseznakValidation(
            barcode = code2, status = ChaseznakStatus.OK, productName = null, expiryDate = null, message = null
        )
        coEvery { chaseznakRepository.sell(code1, any()) } returns ChaseznakResult.Success(code1)
        coEvery { chaseznakRepository.sell(code2, any()) } returns ChaseznakResult.Success(code2)
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.Success(checkId = "ck-3", fiscalSign = "fs", total = 40.0)

        useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        coVerify { chaseznakRepository.sell(code1, "ck-3") }
        coVerify { chaseznakRepository.sell(code2, "ck-3") }
    }

    @Test
    fun `fail after fiscal blocks propagate without calling sell`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.OK, productName = null, expiryDate = null, message = null
        )
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.FiscalError(-1, "Fiscal error")

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 0) { chaseznakRepository.sell(any(), any()) }
    }

    @Test
    fun `mixed cart — unmarked pass, marked validated, all sold in sequence`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = "4600000000000", name = "Milk",
                    quantity = 1.0, price = Money(1000), vatRate = VatRate.VAT_22
                ),
                CartItem(
                    productId = "p2", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.OK, productName = null, expiryDate = null, message = null
        )
        coEvery { chaseznakRepository.sell(code, any()) } returns ChaseznakResult.Success(code)
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.Success(checkId = "ck-4", fiscalSign = "fs", total = 30.0)

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.Success)
        coVerify(exactly = 0) { chaseznakRepository.validateCode("4600000000000") }
        coVerify { chaseznakRepository.validateCode(code) }
        coVerify { chaseznakRepository.sell(code, "ck-4") }
    }

    // ─── STOCK MUTATION (vitbon-kassa-1rd.5.1) ────────────────────────────────

    @Test
    fun `successful sale decrements stock for each cart item`() = runTest {
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = "4600000000000", name = "Milk",
                    quantity = 2.0, price = Money(1000), vatRate = VatRate.VAT_22
                ),
                CartItem(
                    productId = "p2", barcode = "4601111111111", name = "Bread",
                    quantity = 1.0, price = Money(500), vatRate = VatRate.VAT_22
                )
            ),
            paymentType = PaymentType.CASH
        )
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.Success(checkId = "ck-stock", fiscalSign = "fs", total = 25.0)

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.Success)
        coVerify { productRepository.decrementStock("p1", 2.0) }
        coVerify { productRepository.decrementStock("p2", 1.0) }
    }

    @Test
    fun `failed fiscal sale does not decrement stock`() = runTest {
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = "4600000000000", name = "Milk",
                    quantity = 5.0, price = Money(1000), vatRate = VatRate.VAT_22
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { innerUseCase.execute(any(), any(), any(), any(), any(), any()) } returns
            SaleResult.FiscalError(-1, "Fiscal error")

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 0) { productRepository.decrementStock(any(), any()) }
    }

    @Test
    fun `chaseznak block does not decrement stock`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val cart = Cart(
            items = listOf(
                CartItem(
                    productId = "p1", barcode = null, name = "Cigarettes",
                    quantity = 1.0, price = Money(2000), vatRate = VatRate.VAT_22,
                    markedProductCode = code
                )
            ),
            paymentType = PaymentType.CARD
        )
        coEvery { chaseznakRepository.validateCode(code) } returns ChaseznakValidation(
            barcode = code, status = ChaseznakStatus.ALREADY_SOLD,
            productName = null, expiryDate = null, message = null
        )

        val result = useCase.execute(
            cart = cart,
            cashierId = "c1", deviceId = "d1", shiftId = "s1",
            cashierRole = CashierRole.CASHIER, emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 0) { productRepository.decrementStock(any(), any()) }
    }
}
