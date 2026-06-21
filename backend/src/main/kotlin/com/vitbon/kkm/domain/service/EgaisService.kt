package com.vitbon.kkm.domain.service

import com.vitbon.kkm.api.dto.EgaisStatusDto
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * EGAIS proxy service.
 *
 * Forwards incoming XML payloads to the configured УТМ (Универсальный Транспортный Модуль)
 * via the shared [ExternalIntegrationProxyService].
 *
 * Status endpoint does NOT call the upstream — it only performs a TCP-reachability
 * check on the two configured URLs to keep the endpoint cheap and offline-safe.
 */
@Service
class EgaisService(
    private val externalIntegrationProxyService: ExternalIntegrationProxyService,
    @Value("\${integrations.egais.incoming-url:}")
    private val incomingUrl: String,
    @Value("\${integrations.egais.tara-url:}")
    private val taraUrl: String,
    @Value("\${integrations.request-timeout-seconds:10}")
    private val requestTimeoutSeconds: Long
) {
    fun status(): EgaisStatusDto {
        return EgaisStatusDto(
            available = isEndpointReachable(incomingUrl) && isEndpointReachable(taraUrl)
        )
    }

    fun processIncoming(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = incomingUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_XML,
            integrationName = "EGAIS"
        )
    }

    fun processTara(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = taraUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_XML,
            integrationName = "EGAIS"
        )
    }

    private fun isEndpointReachable(endpointUrl: String): Boolean {
        if (endpointUrl.isBlank()) {
            return false
        }

        return runCatching {
            val uri = URI(endpointUrl)
            val host = uri.host ?: return false
            val port = when {
                uri.port != -1 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                uri.scheme.equals("http", ignoreCase = true) -> 80
                else -> return false
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), (requestTimeoutSeconds * 1000).toInt())
            }

            true
        }.getOrDefault(false)
    }
}
