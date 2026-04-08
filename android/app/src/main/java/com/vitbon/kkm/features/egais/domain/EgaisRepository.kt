package com.vitbon.kkm.features.egais.domain

import android.content.Context
import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EgaisRepository"

@Singleton
class EgaisRepository @Inject constructor(
    private val api: VitbonApi
) {
    /**
     * Проверить доступность УТМ.
     * УТМ запущен локально (на сервере бэкенда или на кассе).
     */
    suspend fun checkUtmAvailable(): Boolean {
        return try {
            // Проксируем через бэкенд — он обращается к УТМ
            // Вместо реального ping — простой запрос
            val response = api.egaisIncoming("{}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "УТМ недоступен: ${e.message}")
            false
        }
    }

    /**
     * Принять накладную от ЕГАИС (прокси через бэкенд → УТМ).
     */
    suspend fun acceptIncomingWaybill(waybillXml: String): EgaisResult {
        return try {
            val response = api.egaisIncoming(waybillXml)
            if (response.isSuccessful) {
                EgaisResult.Success(
                    egaisId = extractEgaisId(response.body() ?: ""),
                    message = "Документ загружен в ЕГАИС"
                )
            } else {
                EgaisResult.Error(response.code(), "Ошибка ЕГАИС")
            }
        } catch (e: Exception) {
            EgaisResult.Error(-1, e.message ?: "Сеть недоступна")
        }
    }

    /**
     * Отправить акт вскрытия тары.
     */
    suspend fun sendTaraAct(
        checkId: String,
        productBarcode: String,
        volume: Double
    ): EgaisResult {
        return try {
            val payload = buildTaraAct(checkId, productBarcode, volume)
            val response = api.egaisTara(payload)
            if (response.isSuccessful) {
                EgaisResult.Success(egaisId = checkId, message = "Акт вскрытия тары отправлен")
            } else {
                EgaisResult.Error(response.code(), "Ошибка отправки акта")
            }
        } catch (e: Exception) {
            EgaisResult.Error(-1, e.message ?: "Сеть недоступна")
        }
    }

    private fun extractEgaisId(xml: String): String {
        // Parse EgaisId from XML response
        val match = Regex("""<fsuid:WaybillId>(.*?)</fsuid:WaybillId>""").find(xml)
        return match?.groupValues?.get(1) ?: "UNKNOWN"
    }

    private fun buildTaraAct(checkId: String, productBarcode: String, volume: Double): String {
        return """
            <ns:ActChargeOnWrite>
                <ns:Identity>ACT_TARA_$checkId</ns:Identity>
                <ns:ChargeOnDate>${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}</ns:ChargeOnDate>
                <ns:ProductBarcode>$productBarcode</ns:ProductBarcode>
                <ns:Volume>$volume</ns:Volume>
            </ns:ActChargeOnWrite>
        """.trimIndent()
    }
}
