package com.vitbon.kkm.features.statuses.domain

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * vitbon-kassa-1rd.3.3: Archive OFD evidence for fiscal receipts.
 *
 * Links ФП/ФД/ФН from app/local DB to accepted OFD records.
 * Called after successful fiscal operation to prove receipt reached ОФД.
 */
@Singleton
class OfdEvidenceService @Inject constructor(
    private val checkDao: CheckDao,
    private val api: VitbonApi
) {
    suspend fun archiveEvidence(checkId: String, fnNumber: String, fdNumber: String, fiscalSign: String): Boolean {
        val response = try {
            api.getOfdReceiptStatus(fnNumber = fnNumber, fdNumber = fdNumber, fiscalSign = fiscalSign)
        } catch (_: Throwable) {
            null
        }

        if (response != null && response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                val evidenceJson = buildEvidenceJson(
                    fnNumber = fnNumber,
                    fdNumber = fdNumber,
                    fiscalSign = fiscalSign,
                    ofdRegistrationTime = body.registrationTime,
                    ofdCheckUrl = body.checkUrl,
                    operatorId = body.operatorId,
                    receivedAt = System.currentTimeMillis()
                )
                // Update check with OFD evidence
                checkDao.updateOfdEvidence(checkId, evidenceJson)
                return true
            }
        }
        return false
    }

    private fun buildEvidenceJson(
        fnNumber: String,
        fdNumber: String,
        fiscalSign: String,
        ofdRegistrationTime: Long,
        ofdCheckUrl: String?,
        operatorId: String?,
        receivedAt: Long
    ): String {
        return """
            {"fn":"$fnNumber","fd":"$fdNumber","fiscalSign":"$fiscalSign",
             "ofdRegisteredAt":$ofdRegistrationTime,"checkUrl":"${ofdCheckUrl ?: ""}",
             "operatorId":"${operatorId ?: ""}","archivedAt":$receivedAt}
        """.trimIndent().replace("\n", "")
    }
}