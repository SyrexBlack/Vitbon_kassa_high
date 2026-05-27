package com.vitbon.kkm.core.fiscal.neva

import android.content.Context
import android.util.Log
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckItem
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.CorrectionDoc
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentLine
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeoutException

class Neva01FFiscalCoreTest {

    private val context = mockk<Context>(relaxed = true)
    private val protocol = mockk<Neva01FProtocol>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newCore(): Neva01FFiscalCore {
        return Neva01FFiscalCoreTestable(context, protocol)
    }

    @Test
    fun `printSale delegates to protocol after initialize`() = runBlocking {
        val expected = FiscalResult.Success(
            fiscalSign = "1234567890",
            fnNumber = "9999078900001366",
            fdNumber = "42",
            timestamp = 1_700_000_000_000L
        )
        val core = newCore()
        coEvery { protocol.printSale(sampleSaleCheck, "Kas", null) } returns expected

        assertTrue(core.initialize())

        val result = core.printSale(sampleSaleCheck, "Kas", null)

        assertEquals(expected, result)
        coVerify(exactly = 1) { protocol.printSale(sampleSaleCheck, "Kas", null) }
    }

    @Test
    fun `all fiscal operations delegate to protocol`() = runBlocking {
        val result = FiscalResult.Success(
            fiscalSign = "1234567890",
            fnNumber = "9999078900001366",
            fdNumber = "42",
            timestamp = 1_700_000_000_000L
        )
        val status = FiscalStatus(
            fnRegistered = true,
            fnNumber = "9999078900001366",
            shiftOpen = true,
            shiftAgeHours = 2,
            currentFdNumber = 41,
            ofdConnected = true,
            lastError = null
        )
        val core = newCore()
        coEvery { protocol.openShift() } returns result
        coEvery { protocol.printSale(sampleSaleCheck, "Kas", null) } returns result
        coEvery { protocol.printReturn(sampleReturnCheck, "Kas", null) } returns result
        coEvery { protocol.printCorrection(sampleCorrectionDoc, "Kas", null) } returns result
        coEvery { protocol.printXReport() } returns result
        coEvery { protocol.cashIn(Money(5000), "float") } returns result
        coEvery { protocol.cashOut(Money(2500), "pickup") } returns result
        coEvery { protocol.getStatus() } returns status
        coEvery { protocol.getFFDVersion() } returns FFDVersion.V1_2
        coEvery { protocol.closeShift() } returns result

        assertTrue(core.initialize())

        core.openShift()
        core.printSale(sampleSaleCheck, "Kas", null)
        core.printReturn(sampleReturnCheck, "Kas", null)
        core.printCorrection(sampleCorrectionDoc, "Kas", null)
        core.printXReport()
        core.cashIn(Money(5000), "float")
        core.cashOut(Money(2500), "pickup")
        assertEquals(status, core.getStatus())
        assertEquals(FFDVersion.V1_2, core.getFFDVersion())
        core.closeShift()

        coVerifySequence {
            protocol.openShift()
            protocol.printSale(sampleSaleCheck, "Kas", null)
            protocol.printReturn(sampleReturnCheck, "Kas", null)
            protocol.printCorrection(sampleCorrectionDoc, "Kas", null)
            protocol.printXReport()
            protocol.cashIn(Money(5000), "float")
            protocol.cashOut(Money(2500), "pickup")
            protocol.getStatus()
            protocol.getFFDVersion()
            protocol.closeShift()
        }
    }

    @Test
    fun `printSale fails when core is not initialized`() = runBlocking {
        val core = newCore()

        try {
            core.printSale(sampleSaleCheck, "Kas", null)
            fail("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("SDK not initialized"))
        }

        coVerify(exactly = 0) { protocol.printSale(any(), any(), any()) }
    }

    @Test
    fun `timeout from protocol is mapped as recoverable fiscal exception`() = runBlocking {
        val core = newCore()
        coEvery { protocol.printSale(sampleSaleCheck, "Kas", null) } throws TimeoutException("printer busy")

        assertTrue(core.initialize())

        try {
            core.printSale(sampleSaleCheck, "Kas", null)
            fail("Expected FiscalException")
        } catch (error: FiscalException) {
            assertEquals(-1, error.errorCode)
            assertTrue(error.message!!.contains("printer busy"))
            assertTrue(error.recoverable)
        }
    }

    @Test
    fun `existing fiscal exception is propagated unchanged`() = runBlocking {
        val core = newCore()
        val expected = FiscalException(7, "paper out", recoverable = true)
        coEvery { protocol.openShift() } throws expected

        assertTrue(core.initialize())

        try {
            core.openShift()
            fail("Expected FiscalException")
        } catch (error: FiscalException) {
            assertEquals(expected.errorCode, error.errorCode)
            assertEquals(expected.message, error.message)
            assertTrue(error.recoverable)
        }
    }

    private companion object {
        val sampleSaleCheck = FiscalCheck(
            id = "sale-1",
            type = CheckType.SALE,
            items = listOf(
                CheckItem(
                    id = "item-1",
                    productId = "product-1",
                    barcode = "1234567890123",
                    name = "Milk",
                    quantity = 1.0,
                    price = Money(12_345),
                    discount = Money.ZERO,
                    vatRate = VatRate.VAT_10,
                    total = Money(12_345)
                )
            ),
            payments = listOf(
                PaymentLine(
                    type = PaymentType.CARD,
                    amount = Money(12_345),
                    label = "terminal"
                )
            )
        )

        val sampleReturnCheck = sampleSaleCheck.copy(
            id = "return-1",
            type = CheckType.RETURN
        )

        val sampleCorrectionDoc = CorrectionDoc(
            id = "correction-1",
            type = CheckType.CORRECTION_INCOME,
            baseSum = Money(12_345),
            cashSum = Money(12_345),
            cardSum = Money.ZERO,
            reason = "operator mistake",
            correctionNumber = "1",
            correctionDate = 1_700_000_000_000L,
            vatRate = VatRate.VAT_10
        )
    }
}

/**
 * Testable subclass: injects a spy protocol so tests can mock it,
 * bypassing the real Android service binding.
 */
class Neva01FFiscalCoreTestable(
    context: Context,
    private val testProtocol: Neva01FProtocol
) : Neva01FFiscalCore(context) {
    override fun createProtocol(): Neva01FProtocol = testProtocol
}
