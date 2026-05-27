package com.vitbon.kkm.features.egais.domain

import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.EgaisStatusDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class EgaisRepositoryTest {

    private val api = mockk<VitbonApi>()
    private val repository = EgaisRepository(api)

    @Test
    fun `checkUtmAvailable uses dedicated egais status endpoint`() = runTest {
        coEvery { api.getEgaisStatus() } returns Response.success(EgaisStatusDto(available = true))

        val result = repository.checkUtmAvailable()

        assertTrue(result)
        coVerify(exactly = 1) { api.getEgaisStatus() }
        coVerify(exactly = 0) { api.egaisIncoming(any()) }
    }

    @Test
    fun `checkUtmAvailable fails closed when status endpoint errors`() = runTest {
        coEvery { api.getEgaisStatus() } returns Response.error(
            503,
            "unavailable".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.checkUtmAvailable()

        assertFalse(result)
        coVerify(exactly = 1) { api.getEgaisStatus() }
        coVerify(exactly = 0) { api.egaisIncoming(any()) }
    }
}