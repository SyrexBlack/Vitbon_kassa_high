package com.vitbon.kkm.features.egais.domain

import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import retrofit2.Response

class AgeVerificationUseCaseTest {

    private val api = mockk<VitbonApi>(relaxed = true)
    private val useCase = AgeVerificationUseCase(api)

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `verify sends qr payload as json object to backend`() = runTest {
        coEvery { api.verifyAge(any()) } returns Response.success(
            "{\"verified\":true,\"verificationId\":\"verify-1\"}"
        )

        val result = useCase.verify("MAX-ID-QR")

        coVerify(exactly = 1) { api.verifyAge("{\"qrData\":\"MAX-ID-QR\"}") }
        assertTrue(result.confirmed)
        assertEquals("verify-1", result.verificationId)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `verify returns confirmed false when verified field is false`() = runTest {
        coEvery { api.verifyAge(any()) } returns Response.success(
            "{\"verified\":false,\"verificationId\":\"verify-2\"}"
        )

        val result = useCase.verify("OLD-PASSPORT-QR")

        assertFalse(result.confirmed)
        assertEquals("verify-2", result.verificationId)
        assertNotNull(result.errorMessage)
        val msg = result.errorMessage
        assertTrue(msg != null && msg.contains("не подтверждён"))
    }

    @Test
    fun `verify returns error on non-2xx response`() = runTest {
        coEvery { api.verifyAge(any()) } returns Response.error(
            503,
            "Service unavailable".toResponseBody("text/plain".toMediaType())
        )

        val result = useCase.verify("QR-3")

        assertFalse(result.confirmed)
        val msg = result.errorMessage
        assertTrue(msg != null && msg.contains("503"))
    }

    @Test
    fun `verify returns error on network exception`() = runTest {
        coEvery { api.verifyAge(any()) } throws RuntimeException("connect timed out")

        val result = useCase.verify("QR-4")

        assertFalse(result.confirmed)
        val msg = result.errorMessage
        assertTrue(msg != null && (msg.contains(" недоступна") || msg.contains("timed out")))
    }

    @Test
    fun `verify returns error on empty response body`() = runTest {
        coEvery { api.verifyAge(any()) } returns Response.success("")

        val result = useCase.verify("QR-5")

        assertFalse(result.confirmed)
        val msg = result.errorMessage
        assertTrue(msg != null && msg.contains("не подтверждён"))
    }

    @Test
    fun `verify extracts verificationId from nested JSON`() = runTest {
        coEvery { api.verifyAge(any()) } returns Response.success(
            """
            {"status":"ok","data":{"verified":true,"verificationId":"verify-deep-123","timestamp":1234567890}}
            """.trimIndent()
        )

        val result = useCase.verify("QR-6")

        assertTrue(result.confirmed)
        assertEquals("verify-deep-123", result.verificationId)
    }
}