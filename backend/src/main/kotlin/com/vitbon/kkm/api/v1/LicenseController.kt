package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.*
import com.vitbon.kkm.domain.service.LicenseService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/license")
class LicenseController(private val licenseService: LicenseService) {
    @PostMapping("check")
    fun checkLicense(@RequestBody req: LicenseCheckRequestDto): LicenseCheckResponseDto {
        return licenseService.check(req.deviceId)
    }
}
