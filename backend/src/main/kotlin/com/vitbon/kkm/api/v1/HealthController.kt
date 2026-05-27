package com.vitbon.kkm.api.v1

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Application health and readiness probe.
 * Used by load balancers, orchestrators, and monitoring dashboards.
 */
@RestController
@RequestMapping("/api/v1")
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                service = "vitbon-backend",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @GetMapping("/health/ready")
    fun ready(): ResponseEntity<HealthResponse> {
        // TODO: add db connectivity check, auth service ping
        return ResponseEntity.ok(
            HealthResponse(
                status = "READY",
                service = "vitbon-backend",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    @GetMapping("/health/live")
    fun live(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "ALIVE",
                service = "vitbon-backend",
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: Long
)