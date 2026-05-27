package com.vitbon.kkm.core.fiscal.neva

import android.content.Context
import android.util.Log
import com.vitbon.kkm.core.fiscal.model.*
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that RealNeva01FProtocol delegates all operations to the MSPOS-K bridge,
 * and that the bridge is lazily initialized.
 *
 * Justification: the MSPOS-K binder service is the vendor's current runtime for the
 * Neva 01Ф device. See:
 *   docs/superpowers/specs/2026-05-27-neva01f-sdk-justification-design.md
 */
class RealNeva01FProtocolTest {

    private val context = mockk<Context>(relaxed = true)
    private val bridge = mockk<com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocol>(relaxed = true)

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private fun newProtocol(): RealNeva01FProtocol {
        return RealNeva01FProtocolTestable(context, bridge)
    }

    private val sampleResult = FiscalResult.Success(
        fiscalSign = "7890",
        fnNumber = "fn123",
        fdNumber = "5",
        timestamp = 1_700_000_000_000L
    )
    private val sampleStatus = FiscalStatus(
        fnRegistered = true,
        fnNumber = "fn123",
        shiftOpen = true,
        shiftAgeHours = 1,
        currentFdNumber = 5,
        ofdConnected = true,
        lastError = null
    )

    @Test
    fun `openShift delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.openShift() } returns sampleResult

        val result = protocol.openShift()

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.openShift() }
    }

    @Test
    fun `printSale delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.printSale(any(), any(), any()) } returns sampleResult

        val result = protocol.printSale(sampleCheck, "Kas", null)

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.printSale(sampleCheck, "Kas", null) }
    }

    @Test
    fun `printReturn delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.printReturn(any(), any(), any()) } returns sampleResult

        val result = protocol.printReturn(sampleCheck, "Kas", null)

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.printReturn(sampleCheck, "Kas", null) }
    }

    @Test
    fun `printCorrection delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.printCorrection(any(), any(), any()) } returns sampleResult

        val result = protocol.printCorrection(sampleDoc, "Kas", null)

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.printCorrection(sampleDoc, "Kas", null) }
    }

    @Test
    fun `closeShift delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.closeShift() } returns sampleResult

        val result = protocol.closeShift()

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.closeShift() }
    }

    @Test
    fun `printXReport delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.printXReport() } returns sampleResult

        val result = protocol.printXReport()

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.printXReport() }
    }

    @Test
    fun `cashIn delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.cashIn(any(), any()) } returns sampleResult

        val result = protocol.cashIn(Money(5000), "float")

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.cashIn(Money(5000), "float") }
    }

    @Test
    fun `cashOut delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.cashOut(any(), any()) } returns sampleResult

        val result = protocol.cashOut(Money(2500), "pickup")

        assertEquals(sampleResult, result)
        coVerify(exactly = 1) { bridge.cashOut(Money(2500), "pickup") }
    }

    @Test
    fun `getStatus delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.getStatus() } returns sampleStatus

        val result = protocol.getStatus()

        assertEquals(sampleStatus, result)
        coVerify(exactly = 1) { bridge.getStatus() }
    }

    @Test
    fun `getFFDVersion delegates to MSPOS-K bridge`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.getFFDVersion() } returns FFDVersion.V1_2

        val result = protocol.getFFDVersion()

        assertEquals(FFDVersion.V1_2, result)
        coVerify(exactly = 1) { bridge.getFFDVersion() }
    }

    @Test
    fun `all operations called in sequence pass through in order`() = runBlocking {
        val protocol = newProtocol()
        coEvery { bridge.openShift() } returns sampleResult
        coEvery { bridge.printSale(any(), any(), any()) } returns sampleResult
        coEvery { bridge.closeShift() } returns sampleResult

        protocol.openShift()
        protocol.printSale(sampleCheck, "Kas", null)
        protocol.closeShift()

        coVerifySequence {
            bridge.openShift()
            bridge.printSale(sampleCheck, "Kas", null)
            bridge.closeShift()
        }
    }

    private val sampleCheck = FiscalCheck(
        id = "s1",
        type = CheckType.SALE,
        items = listOf(
            CheckItem(
                id = "i1",
                productId = "p1",
                barcode = "123",
                name = "Tea",
                quantity = 1.0,
                price = Money(9900),
                discount = Money.ZERO,
                vatRate = VatRate.VAT_10,
                total = Money(9900)
            )
        ),
        payments = listOf(
            PaymentLine(type = PaymentType.CARD, amount = Money(9900), label = "card")
        )
    )

    private val sampleDoc = CorrectionDoc(
        id = "c1",
        type = CheckType.CORRECTION_INCOME,
        baseSum = Money(9900),
        cashSum = Money.ZERO,
        cardSum = Money(9900),
        reason = "test",
        correctionNumber = "1",
        correctionDate = 0L,
        vatRate = VatRate.VAT_10
    )
}

/**
 * Testable subclass: injects a spy bridge so tests can verify delegation
 * without triggering real Android service binding.
 */
class RealNeva01FProtocolTestable(
    context: Context,
    private val testBridge: com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocol
) : RealNeva01FProtocol(context) {
    override fun createBridge(): com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocol = testBridge
}