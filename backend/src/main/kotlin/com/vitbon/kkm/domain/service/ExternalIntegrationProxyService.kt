package com.vitbon.kkm.domain.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class ExternalIntegrationProxyService(
    @Value("\${integrations.request-timeout-seconds:10}")
    private val requestTimeoutSeconds: Long
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds))
        .build()

    fun forwardPost(
        endpointUrl: String,
        payload: String,
        contentType: MediaType,
        integrationName: String
    ): ResponseEntity<String> {
        if (endpointUrl.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "$integrationName integration endpoint is not configured"
            )
        }

        val request = try {
            HttpRequest.newBuilder(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "$integrationName integration endpoint is invalid",
                exception
            )
        }

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: IOException) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "$integrationName integration is unavailable",
                exception
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "$integrationName integration request was interrupted",
                exception
            )
        }

        val headers = HttpHeaders()
        response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
            .ifPresent { headers.set(HttpHeaders.CONTENT_TYPE, it) }

        return ResponseEntity.status(response.statusCode())
            .headers(headers)
            .body(response.body())
    }
}