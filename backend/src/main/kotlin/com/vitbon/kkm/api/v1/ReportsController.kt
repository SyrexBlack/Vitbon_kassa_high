package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.MovementReportDto
import com.vitbon.kkm.api.dto.SalesReportDto
import com.vitbon.kkm.domain.service.CheckService
import com.vitbon.kkm.domain.service.DocumentService
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportsController(
    private val checkService: CheckService,
    private val documentService: DocumentService
) {
    @GetMapping
    fun salesReport(
        @RequestParam period: String,
        @RequestParam(required = false) shiftId: String?,
        @RequestParam(required = false) since: Long?
    ): SalesReportDto {
        val principal = SecurityContextHolder.requirePrincipal()
        val checks = checkService.findChecks(
            shiftId = shiftId,
            date = null,
            since = since,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
        return checkService.buildSalesReport(checks, period)
    }

    @GetMapping("/sales")
    fun salesReportAlias(
        @RequestParam period: String,
        @RequestParam(required = false) shiftId: String?,
        @RequestParam(required = false) since: Long?
    ): SalesReportDto {
        return salesReport(period = period, shiftId = shiftId, since = since)
    }

    @GetMapping("/movement")
    fun movementReport(
        @RequestParam period: String,
        @RequestParam(required = false) since: Long?
    ): MovementReportDto {
        val principal = SecurityContextHolder.requirePrincipal()
        val checks = checkService.findChecks(
            shiftId = null,
            date = null,
            since = null,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
        val documents = documentService.findDocuments(
            since = null,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
        return checkService.buildMovementReport(checks = checks, documents = documents, period = period, since = since)
    }
}
