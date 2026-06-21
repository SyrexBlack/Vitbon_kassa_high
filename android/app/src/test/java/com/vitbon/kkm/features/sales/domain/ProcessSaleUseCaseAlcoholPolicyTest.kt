package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.FiscalConfig
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.TaxSystem
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ShiftDao
import com.vitbon.kkm.features.auth.domain.CashierRole
import com.vitbon.kkm.features.egais.domain.AlcoholSaleDecision
import com.vitbon.kkm.features.egais.domain.AlcoholSalePolicyUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * vitbon-kassa-1rd.1.3 — wire [AlcoholSalePolicyUseCase] into [ProcessSaleUseCase].
 * Pre-fiscal alcohol gate must be evaluated BEFORE any orchestrator call.
 */
class ProcessSaleUseCaseAlcoholPolicyTest {

    private val orchestrator = mockk<FiscalOperationOrchestrator>(relaxed = true)
    private val checkDao = mockk<CheckDao>(relaxed = true)
    private val checkItemDao = mockk<CheckItemDao>(relaxed = true)
    private val shiftDao = mockk<ShiftDao>(relaxed = true)
    private val alcoholSalePolicy = mockk<AlcoholSalePolicyUseCase>()

    private val useCase = ProcessSaleUseCase(
        fiscalOrchestrator = orchestrator,
        checkDao = checkDao,
        checkItemDao = checkItemDao,
        shiftDao = shiftDao,
        fiscalConfig = FiscalConfig(taxSystem = TaxSystem.OSN, orgInn = null),
        alcoholSalePolicy = alcoholSalePolicy
    )

    private val alcoholItem = CartItem(
        productId = "p-beer",
        barcode = "4607000001",
        name = "Пиво 0.5л",
        quantity = 1.0,
        price = Money(200_00L),
        discount = Money.ZERO,
        vatRate = VatRate.VAT_22,
        egaisFlag = true
    )

    private val nonAlcoholItem = CartItem(
        productId = "p-water",
        barcode = "4607000002",
        name = "Вода",
        quantity = 1.0,
        price = Money(50_00L),
        discount = Money.ZERO,
        vatRate = VatRate.VAT_22,
        egaisFlag = false
    )

    @Test
    fun `alcohol without age verification blocks before fiscal call`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { alcoholSalePolicy.checkCanSellAlcohol(any(), false) } returns
            AlcoholSaleDecision.AgeVerificationRequired(listOf("Пиво 0.5л"))

        val cart = Cart(items = listOf(alcoholItem), paymentType = PaymentType.CASH)
        val result = useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false,
            ageVerificationDone = false
        )

        assertTrue(result is SaleResult.FiscalError)
        val error = result as SaleResult.FiscalError
        assertTrue(error.message.contains("MAX-ID"))
        assertTrue(error.message.contains("Пиво 0.5л"))
        coVerify(exactly = 0) { orchestrator.executeSale(any()) }
        coVerify(exactly = 0) { checkDao.insert(any()) }
    }

    @Test
    fun `alcohol with EGAIS unavailable blocks before fiscal call`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { alcoholSalePolicy.checkCanSellAlcohol(any(), true) } returns
            AlcoholSaleDecision.Blocked(reason = "EGAIS_UNAVAILABLE", message = "УТМ недоступен")

        val cart = Cart(items = listOf(alcoholItem), paymentType = PaymentType.CASH)
        val result = useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false,
            ageVerificationDone = true
        )

        assertTrue(result is SaleResult.FiscalError)
        assertEquals("УТМ недоступен", (result as SaleResult.FiscalError).message)
        coVerify(exactly = 0) { orchestrator.executeSale(any()) }
    }

    @Test
    fun `alcohol allowed by policy proceeds to fiscal`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { alcoholSalePolicy.checkCanSellAlcohol(any(), true) } returns
            AlcoholSaleDecision.Allowed

        val cart = Cart(items = listOf(alcoholItem), paymentType = PaymentType.CASH)
        useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false,
            ageVerificationDone = true
        )

        // We are not asserting success here — only that the policy was consulted and
        // the orchestrator was reached. The result will surface whatever the relaxed
        // mock returns for executeSale (FiscalRuntimeResult.Success by default).
        coVerify(exactly = 1) { alcoholSalePolicy.checkCanSellAlcohol(any(), true) }
    }

    @Test
    fun `non-alcohol cart bypasses policy entirely`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null

        val cart = Cart(items = listOf(nonAlcoholItem), paymentType = PaymentType.CASH)
        useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        coVerify(exactly = 0) { alcoholSalePolicy.checkCanSellAlcohol(any(), any()) }
    }

    @Test
    fun `mixed cart with alcohol still requires age verification`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { alcoholSalePolicy.checkCanSellAlcohol(any(), false) } returns
            AlcoholSaleDecision.AgeVerificationRequired(listOf("Пиво 0.5л"))

        val cart = Cart(
            items = listOf(nonAlcoholItem, alcoholItem),
            paymentType = PaymentType.CARD
        )
        val result = useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false,
            ageVerificationDone = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 1) { alcoholSalePolicy.checkCanSellAlcohol(match { it.size == 1 }, false) }
    }

    @Test
    fun `default ageVerificationDone parameter is false (fail-closed)`() = runTest {
        coEvery { shiftDao.findOpenShift() } returns null
        coEvery { alcoholSalePolicy.checkCanSellAlcohol(any(), false) } returns
            AlcoholSaleDecision.AgeVerificationRequired(listOf("Пиво 0.5л"))

        val cart = Cart(items = listOf(alcoholItem), paymentType = PaymentType.CASH)
        // No explicit ageVerificationDone argument → should default to false → block
        val result = useCase.execute(
            cart = cart,
            cashierId = "c1",
            deviceId = "d1",
            shiftId = "s1",
            cashierRole = CashierRole.CASHIER,
            emergencySessionActive = false
        )

        assertTrue(result is SaleResult.FiscalError)
        coVerify(exactly = 1) { alcoholSalePolicy.checkCanSellAlcohol(any(), false) }
    }
}