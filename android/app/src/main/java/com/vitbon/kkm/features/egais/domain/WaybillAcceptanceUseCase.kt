package com.vitbon.kkm.features.egais.domain

import javax.inject.Inject
import javax.inject.Singleton

sealed class WaybillAcceptanceResult {
    data class Success(val egaisId: String, val itemsCount: Int) : WaybillAcceptanceResult()
    data class Invalid(val errors: List<String>) : WaybillAcceptanceResult()
    data class Error(val message: String) : WaybillAcceptanceResult()
}

@Singleton
class WaybillAcceptanceUseCase @Inject constructor(
    private val repository: EgaisRepository
) {
    suspend fun acceptWaybill(xml: String): WaybillAcceptanceResult {
        val parser = WaybillParser()
        val parseResult = parser.tryParseWaybillXml(xml)

        val waybill = when (parseResult) {
            is ParseResult.Success -> parseResult.value
            is ParseResult.Error -> return WaybillAcceptanceResult.Invalid(
                listOf("Ошибка парсинга накладной: ${parseResult.message}")
            )
        }

        val errors = mutableListOf<String>()

        if (waybill.items.isEmpty()) {
            errors.add("Накладная не содержит позиций")
        } else {
            errors.addAll(validationErrors(waybill.items))
        }

        if (errors.isNotEmpty()) {
            return WaybillAcceptanceResult.Invalid(errors)
        }

        val sendResult = repository.acceptIncomingWaybill(xml)
        return when (sendResult) {
            is EgaisResult.Success -> WaybillAcceptanceResult.Success(
                egaisId = sendResult.egaisId,
                itemsCount = waybill.items.size
            )
            is EgaisResult.Error -> WaybillAcceptanceResult.Error(sendResult.message)
        }
    }

    private fun validationErrors(items: List<WaybillItem>): List<String> {
        val errors = mutableListOf<String>()
        for (item in items) {
            if (item.volume <= 0) {
                errors.add("Товар \"${item.productName}\": количество должно быть больше нуля")
            }
            if (item.price < 0) {
                errors.add("Товар \"${item.productName}\": цена не может быть отрицательной")
            }
        }
        return errors
    }
}
