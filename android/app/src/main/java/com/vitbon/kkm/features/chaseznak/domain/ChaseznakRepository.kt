package com.vitbon.kkm.features.chaseznak.domain

import com.google.gson.Gson
import com.vitbon.kkm.data.local.dao.MarkingDisposalDao
import com.vitbon.kkm.data.local.entity.LocalMarkingDisposal
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.ChaseznakValidationDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChaseznakRepository @Inject constructor(
    private val api: VitbonApi,
    private val markingDisposalDao: MarkingDisposalDao
) {
    private val gson = Gson()

    /**
     * Проверить код маркировки через локальный модуль ЧЗ или облако.
     */
    suspend fun validateCode(code: String): ChaseznakValidation {
        return try {
            val payload = gson.toJson(ValidatePayload(code = code))
            val response = api.chaseznakValidate(payload)
            if (response.isSuccessful) {
                response.body()?.toDomainModel(fallbackBarcode = code)
                    ?: ChaseznakValidation(
                        barcode = code,
                        status = ChaseznakStatus.ERROR,
                        productName = null,
                        expiryDate = null,
                        message = "Пустой ответ проверки Честный ЗНАК"
                    )
            } else {
                ChaseznakValidation(
                    barcode = code,
                    status = ChaseznakStatus.ERROR,
                    productName = null,
                    expiryDate = null,
                    message = "Ошибка проверки: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            ChaseznakValidation(
                barcode = code,
                status = ChaseznakStatus.ERROR,
                productName = null,
                expiryDate = null,
                message = e.message ?: "Сеть недоступна"
            )
        }
    }

    /**
     * Выбытие кода при продаже.
     * Идемпотентно: если код уже выбыт с тем же checkId — пропускает API-вызов.
     * Результат всегда записывается в marking_disposals для аудита.
     */
    suspend fun sell(code: String, checkId: String): ChaseznakResult {
        val existing = markingDisposalDao.findByCode(code)
        if (existing != null && existing.checkId == checkId && existing.status == "SUCCESS") {
            return ChaseznakResult.Success(code)
        }
        val result: ChaseznakResult = try {
            val payload = gson.toJson(SellPayload(code = code, checkId = checkId))
            val response = api.chaseznakSell(payload)
            if (response.isSuccessful) {
                ChaseznakResult.Success(code)
            } else {
                ChaseznakResult.Error(ChaseznakStatus.ERROR, "Ошибка выбытия: ${response.code()}")
            }
        } catch (e: Exception) {
            ChaseznakResult.Error(ChaseznakStatus.ERROR, e.message ?: "Сеть недоступна")
        }
        markingDisposalDao.insert(
            LocalMarkingDisposal(
                code = code,
                checkId = checkId,
                status = if (result is ChaseznakResult.Success) "SUCCESS" else "FAILED"
            )
        )
        return result
    }

    private fun ChaseznakValidationDto.toDomainModel(fallbackBarcode: String): ChaseznakValidation {
        return ChaseznakValidation(
            barcode = barcode.ifBlank { fallbackBarcode },
            status = status.toDomainStatus(),
            productName = productName,
            expiryDate = expiryDate,
            message = message
        )
    }

    private fun String.toDomainStatus(): ChaseznakStatus {
        return when (uppercase()) {
            "OK" -> ChaseznakStatus.OK
            "NOT_IN_CIRCULATION" -> ChaseznakStatus.NOT_IN_CIRCULATION
            "ALREADY_SOLD" -> ChaseznakStatus.ALREADY_SOLD
            "EXPIRED" -> ChaseznakStatus.EXPIRED
            else -> ChaseznakStatus.ERROR
        }
    }

    private data class ValidatePayload(
        val code: String
    )

    private data class SellPayload(
        val code: String,
        val checkId: String
    )
}
