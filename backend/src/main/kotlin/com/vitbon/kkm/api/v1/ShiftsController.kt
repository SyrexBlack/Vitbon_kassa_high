package com.vitbon.kkm.api.v1

import com.vitbon.kkm.api.dto.ShiftDto
import com.vitbon.kkm.domain.service.ShiftService
import com.vitbon.kkm.domain.service.security.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/shifts")
class ShiftsController(private val shiftService: ShiftService) {
    @GetMapping("{cashierId}")
    fun getShifts(@PathVariable cashierId: String): List<ShiftDto> {
        val principal = SecurityContextHolder.requirePrincipal()
        return shiftService.findByCashier(principal.cashierId.toString())
    }

    @PostMapping
    fun openShift(@RequestBody shift: ShiftDto): ShiftDto {
        val principal = SecurityContextHolder.requirePrincipal()
        return shiftService.open(
            shift = shift,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
    }

    @PutMapping("{id}/close")
    fun closeShift(@PathVariable id: String) {
        val principal = SecurityContextHolder.requirePrincipal()
        shiftService.close(
            id = id,
            cashierId = principal.cashierId.toString(),
            deviceId = principal.deviceId
        )
    }
}
