package com.vitbon.kkm.features.egais.domain

import javax.inject.Inject
import javax.inject.Singleton

sealed class ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>()
    data class Error(val message: String) : ParseResult<Nothing>()
}

@Singleton
class WaybillParser @Inject constructor() {

    fun parseWaybillXml(xml: String): IncomingWaybill {
        return when (val result = tryParseWaybillXml(xml)) {
            is ParseResult.Success -> result.value
            is ParseResult.Error -> throw IllegalArgumentException(result.message)
        }
    }

    fun tryParseWaybillXml(xml: String): ParseResult<IncomingWaybill> {
        return try {
            val waybillId = Regex("""<ns:Identity>(.*?)</ns:Identity>""").find(xml)
                ?.groupValues?.get(1)
                ?: return ParseResult.Error("Не удалось распознать накладную: Identity не найден")

            val positions = Regex("""<ns:Position>(.*?)</ns:Position>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml).toList()

            val items = positions.map { pos ->
                val content = pos.groupValues[1]
                val name = Regex("""<ns:Name>(.*?)</ns:Name>""").find(content)
                    ?.groupValues?.get(1) ?: ""
                val quantity = Regex("""<ns:Quantity>(.*?)</ns:Quantity>""").find(content)
                    ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val price = Regex("""<ns:Price>(.*?)</ns:Price>""").find(content)
                    ?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                val allBarcodes = Regex("""<ns:Barcode>(.*?)</ns:Barcode>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(content).map { it.groupValues[1] }.toList()

                WaybillItem(
                    productName = name,
                    volume = quantity,
                    price = price,
                    barcodes = allBarcodes
                )
            }.toList()

            ParseResult.Success(
                IncomingWaybill(
                    egaisId = waybillId,
                    supplierName = "",
                    date = System.currentTimeMillis(),
                    items = items
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("Не удалось распознать накладную: ${e.message}")
        }
    }
}