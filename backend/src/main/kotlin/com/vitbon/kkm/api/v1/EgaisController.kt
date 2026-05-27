package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.EgaisStatusDto
import com.vitbon.kkm.domain.service.EgaisService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/egais")
class EgaisController(private val egaisService: EgaisService) {
    @GetMapping("status") fun status(): EgaisStatusDto = egaisService.status()
    @PostMapping("incoming") fun incoming(@RequestBody payload: String): ResponseEntity<String> = egaisService.processIncoming(payload)
    @PostMapping("tara") fun tara(@RequestBody payload: String): ResponseEntity<String> = egaisService.processTara(payload)
}
