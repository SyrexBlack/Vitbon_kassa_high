package com.vitbon.kkm.features.egais.domain

import android.util.Log
import com.vitbon.kkm.core.fiscal.model.AgeVerificationResult
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgeVerification"

@Singleton
class AgeVerificationUseCase @Inject constructor(
    private val api: VitbonApi
) {
    /**
     * Проверить возраст через API Цифрового ID Max.
     * QR-код содержит URL или данные с паспорта покупателя.
     *
     * Поток:
     * 1. Кассир сканирует QR с паспорта (QR-код Цифрового ID Max)
     * 2. Извлекает verification_url из QR
     * 3. POST /api/v1/chaseznak/verify-age
     * 4. Результат: confirmed=true → продажа разрешена, confirmed=false → блокировка
     */
    suspend fun verify(qrPayload: String): AgeVerificationResult {
        return try {
            val response = api.verifyAge(qrPayload)
            if (response.isSuccessful) {
                val body = response.body() ?: ""
                // Parse JSON: { "verified": true/false, "verificationId": "..." }
                val verified = body.contains("\"verified\":true", ignoreCase = true)
                AgeVerificationResult(
                    verificationId = extractVerificationId(body),
                    confirmed = verified,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = if (!verified) "Возраст не подтверждён" else null
                )
            } else {
                AgeVerificationResult(
                    verificationId = "",
                    confirmed = false,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = "Ошибка API: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Age verification failed", e)
            AgeVerificationResult(
                verificationId = "",
                confirmed = false,
                timestamp = System.currentTimeMillis(),
                errorMessage = e.message ?: "Сеть недоступна"
            )
        }
    }

    private fun extractVerificationId(json: String): String {
        val match = Regex("\"verificationId\"\\s*:\\s*\"([^\"]+)\"").find(json)
        return match?.groupValues?.get(1) ?: ""
    }
}
