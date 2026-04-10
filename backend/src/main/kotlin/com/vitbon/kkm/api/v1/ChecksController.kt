package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.CheckService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/checks")
class ChecksController(private val checkService: CheckService) {

    @PostMapping("sync")
    fun syncChecks(@RequestBody req: CheckSyncRequestDto): CheckSyncResponseDto {
        return checkService.processSync(req.checks)
    }

    @GetMapping
    fun getChecks(
        @RequestParam shiftId: String?,
        @RequestParam date: String?,
        @RequestParam since: Long?
    ): List<CheckDto> {
        return checkService.findChecks(shiftId, date, since)
    }
}
