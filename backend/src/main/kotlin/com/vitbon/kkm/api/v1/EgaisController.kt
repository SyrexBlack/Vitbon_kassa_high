package com.vitbon.kkm.api.v1

import com.vitbon.kkm.domain.service.EgaisService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("api/v1/egais")
class EgaisController(private val egaisService: EgaisService) {
    @PostMapping("incoming") fun incoming(@RequestBody payload: String): String = egaisService.processIncoming(payload)
    @PostMapping("tara") fun tara(@RequestBody payload: String): String = egaisService.processTara(payload)
}
