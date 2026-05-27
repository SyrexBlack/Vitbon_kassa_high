package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.CheckService
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checks")
class ChecksController(private val checkService: CheckService) {

    @PostMapping
    fun createCheck(@RequestBody check: CheckDto): CheckDto {
        val principal = SecurityContextHolder.requirePrincipal()
        return checkService.create(
            check = check,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
    }

    @GetMapping("{id}")
    fun getCheckById(@PathVariable id: String): ResponseEntity<CheckDto> {
        val principal = SecurityContextHolder.requirePrincipal()
        val dto = checkService.findById(
            id = id,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @PostMapping("sync")
    fun syncChecks(@RequestBody req: CheckSyncRequestDto): CheckSyncResponseDto {
        val principal = SecurityContextHolder.requirePrincipal()
        return checkService.processSync(
            checks = req.checks,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
    }

    @GetMapping
    fun getChecks(
        @RequestParam shiftId: String?,
        @RequestParam date: String?,
        @RequestParam since: Long?
    ): List<CheckDto> {
        val principal = SecurityContextHolder.requirePrincipal()
        return checkService.findChecks(
            shiftId = shiftId,
            date = date,
            since = since,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
    }
}
