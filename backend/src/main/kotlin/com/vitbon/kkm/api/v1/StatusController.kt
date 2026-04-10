package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.StatusResponseDto
import com.vitbon.kkm.domain.service.StatusService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/statuses")
class StatusController(private val statusService: StatusService) {
    @GetMapping
    fun getStatuses(): StatusResponseDto = statusService.getStatuses()
}
