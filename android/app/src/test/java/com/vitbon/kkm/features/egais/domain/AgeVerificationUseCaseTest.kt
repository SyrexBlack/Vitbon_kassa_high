package com.vitbon.kkm.features.egais.domain

import com.vitbon.kkm.data.remote.api.VitbonApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AgeVerificationUseCaseTest {

    private val api = mockk<VitbonApi>()
    private val useCase = AgeVerificationUseCase(api)

    @Test
    fun `verify sends qr payload as json object to backend`() = runBlocking {
        coEvery { api.verifyAge(any()) } returns Response.success(
            "{\"verified\":true,\"verificationId\":\"verify-1\"}"
        )

        val result = useCase.verify("MAX-ID-QR")

        coVerify(exactly = 1) { api.verifyAge("{\"qrData\":\"MAX-ID-QR\"}") }
        assertTrue(result.confirmed)
        assertEquals("verify-1", result.verificationId)
        assertEquals(null, result.errorMessage)
    }
}