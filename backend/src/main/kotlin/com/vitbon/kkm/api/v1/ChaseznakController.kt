package com.vitbon.kkm.api.v1

import com.vitbon.kkm.domain.service.ChaseznakService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/chaseznak")
class ChaseznakController(private val chaseznakService: ChaseznakService) {
    @PostMapping("sell") fun sell(@RequestBody payload: String): String = chaseznakService.processSell(payload)
    @PostMapping("verify-age") fun verifyAge(@RequestBody payload: String): String = chaseznakService.verifyAge(payload)
}
