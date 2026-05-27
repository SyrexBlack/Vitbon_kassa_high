package com.vitbon.kkm.api.v1

import com.vitbon.kkm.domain.service.ChaseznakService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/chaseznak")
class ChaseznakController(private val chaseznakService: ChaseznakService) {
    @PostMapping("validate") fun validate(@RequestBody payload: String): ResponseEntity<String> = chaseznakService.validate(payload)
    @PostMapping("sell") fun sell(@RequestBody payload: String): ResponseEntity<String> = chaseznakService.processSell(payload)
    @PostMapping("verify-age") fun verifyAge(@RequestBody payload: String): ResponseEntity<String> = chaseznakService.verifyAge(payload)
}
