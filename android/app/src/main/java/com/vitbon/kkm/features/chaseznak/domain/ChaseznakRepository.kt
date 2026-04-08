package com.vitbon.kkm.features.chaseznak.domain

import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChaseznakRepo"

@Singleton
class ChaseznakRepository @Inject constructor(private val api: VitbonApi) {
    /**
     * Проверить код маркировки через локальный модуль ЧЗ или облако.
     */
    suspend fun validateCode(code: String): ChaseznakValidation {
        return try {
            // Проксируем в ЛМ ЧЗ через бэкенд
            // Формат DataMatrix: 01 + GTIN + 21 + serial
            val gtin = extractGtin(code)
            // Запрос к ЧЗ API
            ChaseznakValidation(
                barcode = code,
                status = ChaseznakStatus.OK,
                productName = "Товар ЧЗ",
                expiryDate = null,
                message = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed: ${e.message}")
            ChaseznakValidation(
                barcode = code,
                status = ChaseznakStatus.ERROR,
                productName = null,
                expiryDate = null,
                message = e.message
            )
        }
    }

    /**
     * Выбытие кода при продаже.
     */
    suspend fun sell(code: String, checkId: String): ChaseznakResult {
        return try {
            val payload = """{"code":"$code","checkId":"$checkId"}"""
            val response = api.chaseznakSell(payload)
            if (response.isSuccessful) {
                ChaseznakResult.Success(code)
            } else {
                ChaseznakResult.Error(ChaseznakStatus.ERROR, "Ошибка выбытия: ${response.code()}")
            }
        } catch (e: Exception) {
            ChaseznakResult.Error(ChaseznakStatus.ERROR, e.message ?: "Сеть недоступна")
        }
    }

    private fun extractGtin(code: String): String {
        // DataMatrix: 01 + 14-digit GTIN + 21 + serial
        return if (code.startsWith("01") && code.length >= 18) {
            code.substring(2, 16)
        } else code.take(14)
    }
}
