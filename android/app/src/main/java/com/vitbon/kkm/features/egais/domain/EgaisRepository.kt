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
     * Проверить доступность УТМ и вернуть развёрнутый статус.
     * УТМ запущен локально (на сервере бэкенда или на кассе).
     *
     * Статус отражает причину недоступности, чтобы UI мог показать
     * конкретное сообщение вместо generic "УТМ недоступен".
     */
    suspend fun getUtmStatus(): UtmStatus {
        return try {
            val response = api.getEgaisStatus()
            if (response.isSuccessful && response.body()?.available == true) {
                UtmStatus.Ready
            } else {
                val body = response.body()
                when {
                    body == null -> UtmStatus.UnknownError("Пустой ответ УТМ")
                    !body.available -> UtmStatus.Unreachable("УТМ сообщил: недоступен")
                    else -> UtmStatus.UnknownError("Статус УТМ: unknown")
                }
            }
        } catch (e: Exception) {
            when {
                e.message?.contains("401", ignoreCase = true) == true ||
                e.message?.contains("auth", ignoreCase = true) == true ->
                    UtmStatus.AuthError("Ошибка авторизации УТМ: ${e.message}")
                e.message?.contains("connect", ignoreCase = true) == true ||
                e.message?.contains("refused", ignoreCase = true) == true ||
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    UtmStatus.Unreachable(e.message ?: "Сеть недоступна")
                else -> UtmStatus.UnknownError(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    /** Обратная совместимость — булевый checkUtmAvailable(). */
    suspend fun checkUtmAvailable(): Boolean = getUtmStatus() is UtmStatus.Ready

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
