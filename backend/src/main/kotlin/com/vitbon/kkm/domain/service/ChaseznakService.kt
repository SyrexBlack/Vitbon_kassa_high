package com.vitbon.kkm.domain.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

/**
 * Chestny ZNAK (Honest Sign) proxy service.
 *
 * Forwards marking-code validation, sell (write-off) and age-verification requests
 * to the upstream API. The service is intentionally thin: all HTTP/error policy
 * lives in [ExternalIntegrationProxyService] so that every integration behaves the
 * same way (transparent 5xx, structured timeout, content-type preservation).
 */
@Service
class ChaseznakService(
    private val externalIntegrationProxyService: ExternalIntegrationProxyService,
    @Value("\${integrations.chaseznak.sell-url:}")
    private val sellUrl: String,
    @Value("\${integrations.chaseznak.validate-url:}")
    private val validateUrl: String,
    @Value("\${integrations.chaseznak.verify-age-url:}")
    private val verifyAgeUrl: String
) {
    fun validate(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = validateUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Chestny ZNAK"
        )
    }

    fun processSell(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = sellUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Chestny ZNAK"
        )
    }

    fun verifyAge(payload: String): ResponseEntity<String> {
        return externalIntegrationProxyService.forwardPost(
            endpointUrl = verifyAgeUrl,
            payload = payload,
            contentType = MediaType.APPLICATION_JSON,
            integrationName = "Age verification"
        )
    }
}