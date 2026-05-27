package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.AuditSyncRequestDto
import com.vitbon.kkm.api.dto.AuditSyncResponseDto
import com.vitbon.kkm.domain.service.security.AuditService
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val auditService: AuditService
) {
    @PostMapping("sync")
    fun sync(@RequestBody request: AuditSyncRequestDto): AuditSyncResponseDto {
        return auditService.ingestBufferedEvents(
            principal = SecurityContextHolder.requirePrincipal(),
            request = request
        )
    }
}