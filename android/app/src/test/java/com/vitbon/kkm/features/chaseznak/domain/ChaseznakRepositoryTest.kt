package com.vitbon.kkm.features.chaseznak.domain

import com.vitbon.kkm.data.local.dao.MarkingDisposalDao
import com.vitbon.kkm.data.local.entity.LocalMarkingDisposal
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.ChaseznakValidationDto
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ChaseznakRepositoryTest {

    private val api = mockk<VitbonApi>()
    private val markingDisposalDao = mockk<MarkingDisposalDao>(relaxed = true)
    private val repository = ChaseznakRepository(api, markingDisposalDao)

    @Test
    fun `validateCode maps successful backend validation`() = runTest {
        coEvery { api.chaseznakValidate(any()) } returns Response.success(
            ChaseznakValidationDto(
                barcode = "010460123456789021ABC123456789",
                status = "OK",
                productName = "Товар ЧЗ",
                expiryDate = 1_725_811_200_000,
                message = null
            )
        )

        val result = repository.validateCode("010460123456789021ABC123456789")

        assertEquals(ChaseznakStatus.OK, result.status)
        assertEquals("Товар ЧЗ", result.productName)
        assertEquals(1_725_811_200_000, result.expiryDate)
        coVerify(exactly = 1) { api.chaseznakValidate("{\"code\":\"010460123456789021ABC123456789\"}") }
        coVerify(exactly = 0) { api.chaseznakSell(any()) }
    }

    @Test
    fun `validateCode maps backend error response to validation error`() = runTest {
        coEvery { api.chaseznakValidate(any()) } returns Response.error(
            503,
            "unavailable".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.validateCode("010460123456789021ABC123456789")

        assertEquals(ChaseznakStatus.ERROR, result.status)
        assertTrue(result.message?.contains("503") == true)
        coVerify(exactly = 1) { api.chaseznakValidate(any()) }
    }

    @Test
    fun `validateCode maps normalized backend statuses`() = runTest {
        val cases = listOf(
            "NOT_IN_CIRCULATION" to ChaseznakStatus.NOT_IN_CIRCULATION,
            "ALREADY_SOLD" to ChaseznakStatus.ALREADY_SOLD,
            "EXPIRED" to ChaseznakStatus.EXPIRED
        )

        cases.forEach { (apiStatus, expectedStatus) ->
            clearMocks(api)
            coEvery { api.chaseznakValidate(any()) } returns Response.success(
                ChaseznakValidationDto(
                    barcode = "010460123456789021ABC123456789",
                    status = apiStatus,
                    productName = "Товар ЧЗ",
                    expiryDate = null,
                    message = apiStatus
                )
            )

            val result = repository.validateCode("010460123456789021ABC123456789")

            assertEquals(expectedStatus, result.status)
            assertEquals(apiStatus, result.message)
        }
    }

    @Test
    fun `validateCode serializes special characters safely`() = runTest {
        val code = "01\"ABCXYZ\\Q"
        coEvery { api.chaseznakValidate(any()) } returns Response.success(
            ChaseznakValidationDto(
                barcode = code,
                status = "OK",
                productName = "Товар ЧЗ",
                expiryDate = null,
                message = null
            )
        )

        repository.validateCode(code)

        coVerify(exactly = 1) {
            api.chaseznakValidate(match {
                it == "{\"code\":\"01\\\"ABC\\u001dXYZ\\\\Q\"}"
            })
        }
    }

    @Test
    fun `sell serializes special characters safely`() = runTest {
        val code = "01\"ABCXYZ\\Q"
        val checkId = "CHK\"-1"
        coEvery { api.chaseznakSell(any()) } returns Response.success("{}")
        coEvery { markingDisposalDao.findByCode(code) } returns null

        val result = repository.sell(code, checkId)

        assertEquals(ChaseznakResult.Success(code), result)
        coVerify(exactly = 1) {
            api.chaseznakSell(match {
                it == "{\"code\":\"01\\\"ABC\\u001dXYZ\\\\Q\",\"checkId\":\"CHK\\\"-1\"}"
            })
        }
    }

    @Test
    fun `sell is idempotent — skips API when already disposed with same checkId`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val checkId = "ck-1"
        coEvery { markingDisposalDao.findByCode(code) } returns LocalMarkingDisposal(
            code = code,
            checkId = checkId,
            disposedAt = System.currentTimeMillis(),
            status = "SUCCESS"
        )

        val result = repository.sell(code, checkId)

        assertTrue(result is ChaseznakResult.Success)
        coVerify(exactly = 0) { api.chaseznakSell(any()) }
    }

    @Test
    fun `sell calls API when code was previously disposed with different checkId`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        coEvery { markingDisposalDao.findByCode(code) } returns LocalMarkingDisposal(
            code = code,
            checkId = "ck-old",
            disposedAt = System.currentTimeMillis(),
            status = "SUCCESS"
        )
        coEvery { api.chaseznakSell(any()) } returns Response.success("{}")

        val result = repository.sell(code, "ck-new")

        assertTrue(result is ChaseznakResult.Success)
        coVerify(exactly = 1) { api.chaseznakSell(any()) }
    }

    @Test
    fun `sell records successful disposal in marking_disposals table`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val checkId = "ck-1"
        coEvery { markingDisposalDao.findByCode(code) } returns null
        coEvery { api.chaseznakSell(any()) } returns Response.success("{}")

        repository.sell(code, checkId)

        coVerify { markingDisposalDao.insert(match<LocalMarkingDisposal> {
            it.code == code && it.checkId == checkId && it.status == "SUCCESS"
        }) }
    }

    @Test
    fun `sell records failed disposal in marking_disposals table on API error`() = runTest {
        val code = "01046123456789052FnS+EqV1XNAmLqT"
        val checkId = "ck-1"
        coEvery { markingDisposalDao.findByCode(code) } returns null
        coEvery { api.chaseznakSell(any()) } returns Response.error(500, "{}".toResponseBody(null))

        val result = repository.sell(code, checkId)

        assertTrue(result is ChaseznakResult.Error)
        coVerify { markingDisposalDao.insert(match<LocalMarkingDisposal> {
            it.code == code && it.checkId == checkId && it.status == "FAILED"
        }) }
    }
}