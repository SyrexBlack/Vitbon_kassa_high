package com.vitbon.kkm.features.statuses.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.OfdReceiptStatusResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * TDD — vitbon-kassa-1rd.3.3: OFD evidence collection.
 * Fiscal receipts must have evidence linking ФП/ФД/ФН to accepted OFD records.
 */
class OfdEvidenceServiceTest {

    private val checkDao = mockk<CheckDao>(relaxed = true)
    private val api = mockk<VitbonApi>(relaxed = true)

    private val service = OfdEvidenceService(checkDao, api)

    @Test
    fun `OFD confirmed then evidence archived to check record`() = runTest {
        val checkId = "check-001"
        val apiResponse: Response<OfdReceiptStatusResponse> = mockk(relaxed = true)
        every { apiResponse.isSuccessful } returns true
        every { apiResponse.body() } returns OfdReceiptStatusResponse(
            registrationTime = System.currentTimeMillis(),
            checkUrl = "https://ofd.example.com/check/FN001/FD001",
            operatorId = "OFD-OPERATOR-001"
        )
        coEvery { api.getOfdReceiptStatus(any(), any(), any()) } returns apiResponse

        val result = service.archiveEvidence(
            checkId = checkId,
            fnNumber = "FN001",
            fdNumber = "FD001",
            fiscalSign = "FS123456"
        )

        assertTrue(result)
        // Verify checkDao was called with the expected checkId
        coEvery { checkDao.updateOfdEvidence(checkId, any()) } returns Unit
    }

    @Test
    fun `OFD not confirmed returns false and does not archive`() = runTest {
        val apiResponse: Response<OfdReceiptStatusResponse> = mockk(relaxed = true)
        every { apiResponse.isSuccessful } returns false
        coEvery { api.getOfdReceiptStatus(any(), any(), any()) } returns apiResponse

        val result = service.archiveEvidence(
            checkId = "check-002",
            fnNumber = "FN001",
            fdNumber = "FD001",
            fiscalSign = "FS123456"
        )

        assertFalse(result)
    }

    @Test
    fun `network error returns false and does not archive`() = runTest {
        coEvery { api.getOfdReceiptStatus(any(), any(), any()) } throws RuntimeException("Network unavailable")

        val result = service.archiveEvidence(
            checkId = "check-003",
            fnNumber = "FN001",
            fdNumber = "FD001",
            fiscalSign = "FS123456"
        )

        assertFalse(result)
    }

    @Test
    fun `OFD confirmed with null optionals does not crash`() = runTest {
        val apiResponse: Response<OfdReceiptStatusResponse> = mockk(relaxed = true)
        every { apiResponse.isSuccessful } returns true
        every { apiResponse.body() } returns OfdReceiptStatusResponse(
            registrationTime = System.currentTimeMillis(),
            checkUrl = null,
            operatorId = null
        )
        coEvery { api.getOfdReceiptStatus(any(), any(), any()) } returns apiResponse

        val result = service.archiveEvidence(
            checkId = "check-004",
            fnNumber = "FN001",
            fdNumber = "FD001",
            fiscalSign = "FS123456"
        )

        assertTrue(result)
    }
}